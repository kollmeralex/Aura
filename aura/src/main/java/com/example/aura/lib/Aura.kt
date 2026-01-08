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

    /**
     * Counterbalancing modes for experiment condition ordering
     */
    enum class CounterbalanceMode {
        /** Latin Square: Balanced rotation of conditions (recommended) */
        LATIN_SQUARE,
        /** Full Permutation: All n! orderings, cycling through participants */
        FULL_PERMUTATION,
        /** Random: Completely randomized order for each participant */
        RANDOM,
        /** Custom: Experimenter defines the exact order per participant */
        CUSTOM,
        /** Legacy: Simple odd/even reversal (backward compatible) */
        LEGACY
    }

    /**
     * Configuration for counterbalancing
     */
    data class CounterbalanceConfig(
        /** The counterbalancing mode to use */
        val mode: CounterbalanceMode = CounterbalanceMode.LATIN_SQUARE,
        /** Custom Latin Square matrix (rows = participant groups, cols = condition order) */
        val customLatinSquare: List<List<String>>? = null,
        /** Custom condition order for CUSTOM mode (per participant ID) */
        val customOrders: Map<String, List<String>>? = null,
        /** Starting condition (optional, will be first in the order) */
        val startCondition: String? = null,
        /** Fixed ending condition (optional, will be last in the order) */
        val endCondition: String? = null
    )

    /**
     * Result object returned when initializing counterbalancing
     */
    data class CounterbalanceResult(
        /** The condition order for this participant */
        val conditionOrder: List<String>,
        /** Which Latin Square row (or permutation index) was used */
        val groupIndex: Int,
        /** The mode that was used */
        val mode: CounterbalanceMode,
        /** Total number of groups/permutations available */
        val totalGroups: Int,
        /** The complete Latin Square or permutation matrix (for experimenter reference) */
        val allOrders: List<List<String>>
    )

    data class Config(
        val context: Context,
        val experimentID: String,
        val userID: String,
        val couchDbUrl: String,
        val dbName: String,
        val username: String,
        val password: String,
        val availableConditions: List<String> = emptyList(),
        val counterbalanceConfig: CounterbalanceConfig = CounterbalanceConfig()
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

    // ==================== COUNTERBALANCING ====================

    /**
     * Get the counterbalanced condition order for current participant.
     * Returns a CounterbalanceResult with the order and metadata.
     * 
     * Example usage:
     * ```
     * val result = Aura.getCounterbalancedOrder()
     * println("Order for this participant: ${result.conditionOrder}")
     * println("Group index: ${result.groupIndex} of ${result.totalGroups}")
     * println("All possible orders: ${result.allOrders}")
     * ```
     */
    fun getCounterbalancedOrder(): CounterbalanceResult {
        val config = currentConfig ?: throw IllegalStateException("Aura not initialized! Call setupExperiment first.")
        val conditions = config.availableConditions
        val cbConfig = config.counterbalanceConfig
        
        if (conditions.isEmpty()) {
            return CounterbalanceResult(
                conditionOrder = emptyList(),
                groupIndex = 0,
                mode = cbConfig.mode,
                totalGroups = 0,
                allOrders = emptyList()
            )
        }
        
        if (conditions.size == 1) {
            return CounterbalanceResult(
                conditionOrder = conditions,
                groupIndex = 0,
                mode = cbConfig.mode,
                totalGroups = 1,
                allOrders = listOf(conditions)
            )
        }
        
        val participantIndex = config.userID.toIntOrNull() ?: config.userID.hashCode().let { 
            if (it < 0) -it else it 
        }
        
        return when (cbConfig.mode) {
            CounterbalanceMode.LATIN_SQUARE -> getLatinSquareOrder(conditions, participantIndex, cbConfig)
            CounterbalanceMode.FULL_PERMUTATION -> getFullPermutationOrder(conditions, participantIndex, cbConfig)
            CounterbalanceMode.RANDOM -> getRandomOrder(conditions, participantIndex, cbConfig)
            CounterbalanceMode.CUSTOM -> getCustomOrder(conditions, config.userID, cbConfig)
            CounterbalanceMode.LEGACY -> getLegacyOrder(conditions, participantIndex, cbConfig)
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use getCounterbalancedOrder() instead
     */
    fun getSuggestedConditionOrder(): List<String> {
        return try {
            getCounterbalancedOrder().conditionOrder
        } catch (e: Exception) {
            Log.e(TAG, "Error getting counterbalanced order", e)
            currentConfig?.availableConditions ?: emptyList()
        }
    }

    /**
     * Generate a Latin Square for the given conditions.
     * 
     * For n conditions, generates an n×n Latin Square where:
     * - Each row represents one participant group
     * - Each condition appears exactly once in each row and column
     * 
     * Example for 3 conditions (A, B, C):
     * ```
     * Row 0: A → B → C
     * Row 1: B → C → A
     * Row 2: C → A → B
     * ```
     */
    fun generateLatinSquare(conditions: List<String>): List<List<String>> {
        val n = conditions.size
        return (0 until n).map { row ->
            (0 until n).map { col ->
                conditions[(row + col) % n]
            }
        }
    }

    /**
     * Generate all possible permutations (n!) for the given conditions.
     * Warning: This grows factorially! Only practical for small n (≤6).
     */
    fun generateAllPermutations(conditions: List<String>): List<List<String>> {
        if (conditions.size <= 1) return listOf(conditions)
        
        val result = mutableListOf<List<String>>()
        permute(conditions.toMutableList(), 0, result)
        return result
    }

    private fun permute(arr: MutableList<String>, start: Int, result: MutableList<List<String>>) {
        if (start == arr.size - 1) {
            result.add(arr.toList())
            return
        }
        for (i in start until arr.size) {
            arr[start] = arr[i].also { arr[i] = arr[start] }
            permute(arr, start + 1, result)
            arr[start] = arr[i].also { arr[i] = arr[start] }
        }
    }

    private fun getLatinSquareOrder(
        conditions: List<String>,
        participantIndex: Int,
        cbConfig: CounterbalanceConfig
    ): CounterbalanceResult {
        val latinSquare = cbConfig.customLatinSquare ?: generateLatinSquare(conditions)
        val groupIndex = participantIndex % latinSquare.size
        var order = latinSquare[groupIndex].toMutableList()
        
        // Apply start/end condition constraints
        order = applyConstraints(order, cbConfig)
        
        return CounterbalanceResult(
            conditionOrder = order,
            groupIndex = groupIndex,
            mode = CounterbalanceMode.LATIN_SQUARE,
            totalGroups = latinSquare.size,
            allOrders = latinSquare
        )
    }

    private fun getFullPermutationOrder(
        conditions: List<String>,
        participantIndex: Int,
        cbConfig: CounterbalanceConfig
    ): CounterbalanceResult {
        val allPermutations = generateAllPermutations(conditions)
        val groupIndex = participantIndex % allPermutations.size
        var order = allPermutations[groupIndex].toMutableList()
        
        order = applyConstraints(order, cbConfig)
        
        return CounterbalanceResult(
            conditionOrder = order,
            groupIndex = groupIndex,
            mode = CounterbalanceMode.FULL_PERMUTATION,
            totalGroups = allPermutations.size,
            allOrders = allPermutations
        )
    }

    private fun getRandomOrder(
        conditions: List<String>,
        participantIndex: Int,
        cbConfig: CounterbalanceConfig
    ): CounterbalanceResult {
        // Use participant index as seed for reproducibility
        val random = java.util.Random(participantIndex.toLong())
        var order = conditions.shuffled(random).toMutableList()
        
        order = applyConstraints(order, cbConfig)
        
        return CounterbalanceResult(
            conditionOrder = order,
            groupIndex = participantIndex,
            mode = CounterbalanceMode.RANDOM,
            totalGroups = -1, // Infinite for random
            allOrders = listOf(order) // Only this participant's order
        )
    }

    private fun getCustomOrder(
        conditions: List<String>,
        participantId: String,
        cbConfig: CounterbalanceConfig
    ): CounterbalanceResult {
        val customOrders = cbConfig.customOrders ?: throw IllegalArgumentException(
            "CUSTOM mode requires customOrders to be set in CounterbalanceConfig"
        )
        
        var order = customOrders[participantId]?.toMutableList() ?: run {
            // Fallback: try to parse as number and use modulo
            val index = participantId.toIntOrNull()
            if (index != null && customOrders.isNotEmpty()) {
                val keys = customOrders.keys.toList()
                customOrders[keys[index % keys.size]]?.toMutableList()
            } else {
                null
            }
        } ?: conditions.toMutableList() // Ultimate fallback
        
        order = applyConstraints(order, cbConfig)
        
        return CounterbalanceResult(
            conditionOrder = order,
            groupIndex = customOrders.keys.indexOf(participantId).let { if (it < 0) 0 else it },
            mode = CounterbalanceMode.CUSTOM,
            totalGroups = customOrders.size,
            allOrders = customOrders.values.toList()
        )
    }

    private fun getLegacyOrder(
        conditions: List<String>,
        participantIndex: Int,
        cbConfig: CounterbalanceConfig
    ): CounterbalanceResult {
        val order = if (participantIndex % 2 == 0) {
            conditions.toMutableList()
        } else {
            conditions.asReversed().toMutableList()
        }
        
        val finalOrder = applyConstraints(order, cbConfig)
        
        return CounterbalanceResult(
            conditionOrder = finalOrder,
            groupIndex = participantIndex % 2,
            mode = CounterbalanceMode.LEGACY,
            totalGroups = 2,
            allOrders = listOf(conditions, conditions.asReversed())
        )
    }

    private fun applyConstraints(order: MutableList<String>, cbConfig: CounterbalanceConfig): MutableList<String> {
        // Apply start condition constraint
        cbConfig.startCondition?.let { start ->
            if (order.contains(start)) {
                order.remove(start)
                order.add(0, start)
            }
        }
        
        // Apply end condition constraint
        cbConfig.endCondition?.let { end ->
            if (order.contains(end)) {
                order.remove(end)
                order.add(end)
            }
        }
        
        return order
    }

    /**
     * Helper function to get a printable summary of all counterbalancing groups.
     * Useful for experimenters to see the full Latin Square or permutation table.
     */
    fun getCounterbalancingSummary(): String {
        val config = currentConfig ?: return "Aura not initialized"
        val result = getCounterbalancedOrder()
        
        return buildString {
            appendLine("=== Counterbalancing Summary ===")
            appendLine("Mode: ${result.mode}")
            appendLine("Conditions: ${config.availableConditions}")
            appendLine("Current Participant: ${config.userID} (Group ${result.groupIndex})")
            appendLine("Current Order: ${result.conditionOrder.joinToString(" → ")}")
            appendLine()
            appendLine("All Groups (${result.totalGroups} total):")
            result.allOrders.forEachIndexed { index, order ->
                appendLine("  Group $index: ${order.joinToString(" → ")}")
            }
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
            fields = listOf("condition", "timestamp")
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
            fields = listOf("condition", "timestamp")
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

    /**
     * Execute a raw Mango query against CouchDB
     * @param query JSON string containing a Mango query
     * @return JSON response string or null if failed
     */
    fun executeRawQuery(query: String): String? {
        val config = currentConfig ?: run {
            Log.e(TAG, "executeRawQuery called before setupExperiment")
            return null
        }

        val localApi = api ?: run {
            Log.e(TAG, "API not initialized")
            return null
        }

        return try {
            val queryMap = gson.fromJson(query, Map::class.java) as Map<String, Any>
            val mangoQuery = MangoQuery(
                selector = queryMap["selector"] as? Map<String, Any> ?: emptyMap(),
                fields = queryMap["fields"] as? List<String>,
                limit = (queryMap["limit"] as? Double)?.toInt()
            )

            val response = localApi.findDocuments(config.dbName, authHeader ?: "", mangoQuery).execute()
            if (response.isSuccessful) {
                gson.toJson(response.body())
            } else {
                Log.e(TAG, "Query failed: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeRawQuery error", e)
            null
        }
    }
}
