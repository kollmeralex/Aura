package com.example.aura.lib

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.aura.lib.network.CouchDbApi
import com.example.aura.lib.network.LogEntry
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
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
        
        // Setup Log File: Android/data/packagename/files/aura_logs/ExpID_UserID.jsonl
        val fileName = "${config.experimentID}_${config.userID}.jsonl"
        val dir = config.context.getExternalFilesDir("aura_logs") ?: config.context.filesDir
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, fileName)
        Log.i(TAG, "Local logging to: ${logFile?.absolutePath}")

        // Create Basic Auth Header
        val credentials = "${config.username}:${config.password}"
        authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        // Initialize Retrofit
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

    /**
     * Returns a suggested order of conditions for this user.
     * Currently implements a simple deterministic counterbalancing (A/B vs B/A) based on UserID hash.
     * In future versions, this could query the server.
     */
    fun getSuggestedConditionOrder(): List<String> {
        val config = currentConfig ?: return emptyList()
        val conditions = config.availableConditions
        
        if (conditions.isEmpty()) return emptyList()
        if (conditions.size == 1) return conditions

        // Deterministic shuffle based on UserID
        // If UserID is integer-like "1", "2", use it directly, else hashCode
        val idVal = config.userID.toIntOrNull() ?: config.userID.hashCode()
        
        // Simple Modulo 2 for 2 conditions
        return if (idVal % 2 == 0) {
            conditions // Original Order
        } else {
            conditions.reversed() // Reversed Order
        }
    }

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

        // 1. Local Storage (JSON Lines)
        logToDisk(entry)

        // 2. Network Upload
        Log.v(TAG, "Sending Event: $entry")
        api?.postLog(config.dbName, authHeader ?: "", entry)?.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    Log.v(TAG, "Event sent successfully.")
                } else {
                    Log.e(TAG, "Failed to send event: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                Log.e(TAG, "Network error sending event: ${t.message}")
            }
        })
    }

    private fun logToDisk(entry: LogEntry) {
        val file = logFile ?: return
        try {
            // Append mode = true
            FileWriter(file, true).use { writer ->
                writer.append(gson.toJson(entry)).append("\n")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to disk", e)
        }
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