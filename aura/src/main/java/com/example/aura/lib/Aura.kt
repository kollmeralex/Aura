package com.example.aura.lib

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.aura.lib.network.CouchDbApi
import com.example.aura.lib.network.FindResponse
import com.example.aura.lib.network.LogEntry
import com.example.aura.lib.network.MangoQuery
import com.example.aura.lib.worker.UploadWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Aura {
    private const val TAG = "AuraLib"
    private var currentConfig: Config? = null
    private var currentCondition: String = "Unknown" // Default
    private var api: CouchDbApi? = null
    private var authHeader: String? = null
    private val gson = Gson()
    private var logFile: File? = null
    private var queueFile: File? = null

    data class Config(
        val context: Context,
        val experimentID: String,
        val userID: String,
        val couchDbUrl: String,
        val dbName: String,
        val username: String,
        val password: String,
        val availableConditions: List<String> = emptyList()
    )

    fun setupExperiment(config: Config) {
        Log.d(TAG, "Setup Experiment: $config")
        currentConfig = config
        
        // Setup Log File: Archive
        val fileName = "${config.experimentID}_${config.userID}.jsonl"
        val dir = config.context.getExternalFilesDir("aura_logs") ?: config.context.filesDir
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, fileName)
        Log.i(TAG, "Local logging to: ${logFile?.absolutePath}")

        // Setup Queue File: Buffer for Sync
        val queueDir = config.context.getExternalFilesDir("aura_queue") ?: config.context.filesDir
        if (!queueDir.exists()) queueDir.mkdirs()
        queueFile = File(queueDir, "current_queue.jsonl")

        // Create Basic Auth Header
        val credentials = "${config.username}:${config.password}"
        authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        // Initialize Retrofit (kept for future bidirectional features)
        val baseUrl = if (config.couchDbUrl.endsWith("/")) config.couchDbUrl else "${config.couchDbUrl}/"
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(CouchDbApi::class.java)
    }

    fun setCondition(condition: String) {
        currentCondition = condition
        Log.d(TAG, "Condition set to: $condition")
        logEvent("condition_started", mapOf("new_condition" to condition))
    }

    fun getSuggestedConditionOrder(): List<String> {
        val config = currentConfig ?: return emptyList()
        val conditions = config.availableConditions
        
        if (conditions.isEmpty()) return emptyList()
        if (conditions.size == 1) return conditions

        val idVal = config.userID.toIntOrNull() ?: config.userID.hashCode()
        
        return if (idVal % 2 == 0) {
            conditions 
        } else {
            conditions.asReversed() // Use asReversed() for API compatibility
        }
    }

    // ==================== BIDIRECTIONAL: Server → App ====================

    /**
     * Fetch completed conditions for current user from server.
     * Queries CouchDB for all "condition_started" events logged by this user.
     * 
     * @return List of condition names that have been started by this user
     * @throws Exception on network or parsing errors
     */
    suspend fun getCompletedConditions(): List<String> = withContext(Dispatchers.IO) {
        val config = currentConfig ?: throw IllegalStateException("Aura not initialized! Call setupExperiment first.")
        val currentApi = api ?: throw IllegalStateException("API not initialized!")
        val auth = authHeader ?: throw IllegalStateException("Auth header not set!")

        val query = MangoQuery(
            selector = mapOf(
                "user_id" to config.userID,
                "experiment_id" to config.experimentID,
                "event_name" to "condition_started"
            ),
            fields = listOf("condition", "timestamp"),
            sort = listOf(mapOf("timestamp" to "asc"))
        )

        try {
            val response = currentApi.findDocuments(config.dbName, auth, query).execute()
            if (response.isSuccessful) {
                val findResponse = response.body()
                val conditions = findResponse?.docs?.map { it.condition }?.distinct() ?: emptyList()
                Log.d(TAG, "Fetched completed conditions from server: $conditions")
                conditions
            } else {
                Log.e(TAG, "Failed to fetch conditions: ${response.code()} - ${response.message()}")
                throw Exception("Server error: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching conditions", e)
            throw e
        }
    }

    /**
     * Callback-based alternative for non-coroutine users.
     * Fetches completed conditions and calls appropriate callback.
     */
    fun getCompletedConditions(
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val config = currentConfig ?: run {
            onError(IllegalStateException("Aura not initialized! Call setupExperiment first."))
            return
        }
        val currentApi = api ?: run {
            onError(IllegalStateException("API not initialized!"))
            return
        }
        val auth = authHeader ?: run {
            onError(IllegalStateException("Auth header not set!"))
            return
        }

        val query = MangoQuery(
            selector = mapOf(
                "user_id" to config.userID,
                "experiment_id" to config.experimentID,
                "event_name" to "condition_started"
            ),
            fields = listOf("condition", "timestamp"),
            sort = listOf(mapOf("timestamp" to "asc"))
        )

        Thread {
            try {
                val response = currentApi.findDocuments(config.dbName, auth, query).execute()
                if (response.isSuccessful) {
                    val findResponse = response.body()
                    val conditions = findResponse?.docs?.map { it.condition }?.distinct() ?: emptyList()
                    Log.d(TAG, "Fetched completed conditions from server: $conditions")
                    onSuccess(conditions)
                } else {
                    Log.e(TAG, "Failed to fetch conditions: ${response.code()} - ${response.message()}")
                    onError(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching conditions", e)
                onError(e)
            }
        }.start()
    }

    /**
     * Get smart counterbalancing based on server state.
     * Queries server to determine which conditions user has completed,
     * then returns remaining conditions in counterbalanced order.
     * 
     * Falls back to local getSuggestedConditionOrder() on network error.
     * 
     * @return List of remaining conditions to complete, or all conditions on error
     */
    suspend fun getServerAwareConditionOrder(): List<String> = withContext(Dispatchers.IO) {
        val config = currentConfig ?: return@withContext emptyList()
        
        try {
            val completedConditions = getCompletedConditions()
            val allConditions = getSuggestedConditionOrder()
            
            // Filter out already completed conditions
            val remainingConditions = allConditions.filter { it !in completedConditions }
            
            Log.d(TAG, "Server-aware order: completed=$completedConditions, remaining=$remainingConditions")
            
            if (remainingConditions.isEmpty()) {
                // User has completed all conditions
                Log.i(TAG, "User has completed all conditions!")
                emptyList()
            } else {
                remainingConditions
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get server state, falling back to local counterbalancing", e)
            // Fallback to local counterbalancing
            getSuggestedConditionOrder()
        }
    }

    /**
     * Callback-based alternative for getServerAwareConditionOrder.
     */
    fun getServerAwareConditionOrder(
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        getCompletedConditions(
            onSuccess = { completedConditions ->
                val allConditions = getSuggestedConditionOrder()
                val remainingConditions = allConditions.filter { it !in completedConditions }
                Log.d(TAG, "Server-aware order: completed=$completedConditions, remaining=$remainingConditions")
                onSuccess(remainingConditions)
            },
            onError = { e ->
                Log.w(TAG, "Failed to get server state, falling back to local counterbalancing", e)
                // Fallback to local counterbalancing
                onSuccess(getSuggestedConditionOrder())
            }
        )
    }

    // ==================== END BIDIRECTIONAL ====================

    fun logEvent(eventName: String, data: Map<String, Any>) {
        val config = currentConfig ?: run {
            Log.e(TAG, "Aura not initialized! Call setupExperiment first.")
            return
        }

        val entry = LogEntry(
            experimentID = config.experimentID,
            userID = config.userID,
            condition = currentCondition,
            eventName = eventName,
            timestamp = System.currentTimeMillis(),
            payload = data
        )

        // 1. Local Storage (Archive)
        logToDisk(entry, logFile)

        // 2. Queue for Sync
        logToDisk(entry, queueFile)

        // 3. Trigger Sync
        scheduleSync(config)
    }

    private fun logToDisk(entry: LogEntry, file: File?) {
        if (file == null) return
        try {
            FileWriter(file, true).use { writer ->
                writer.append(gson.toJson(entry)).append("\n")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to disk: ${file.name}", e)
        }
    }

    private fun scheduleSync(config: Config) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putString(UploadWorker.KEY_BASE_URL, config.couchDbUrl)
            .putString(UploadWorker.KEY_AUTH_HEADER, authHeader)
            .putString(UploadWorker.KEY_DB_NAME, config.dbName)
            .build()

        val uploadWork = OneTimeWorkRequest.Builder(UploadWorker::class.java)
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(config.context)
            .enqueueUniqueWork("AuraSync", ExistingWorkPolicy.APPEND, uploadWork)
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
