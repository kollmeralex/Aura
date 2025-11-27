package com.example.aura.lib

import android.util.Base64
import android.util.Log
import com.example.aura.lib.network.CouchDbApi
import com.example.aura.lib.network.LogEntry
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Aura {
    private const val TAG = "AuraLib"
    private var currentConfig: Config? = null
    private var api: CouchDbApi? = null
    private var authHeader: String? = null

    data class Config(
        val experimentID: String,
        val condition: String,
        val userID: String,
        val couchDbUrl: String, // e.g., "http://192.168.178.20:5984/"
        val dbName: String,
        val username: String,
        val password: String
    )

    fun setupExperiment(config: Config) {
        Log.d(TAG, "Setup Experiment: $config")
        currentConfig = config

        // Create Basic Auth Header
        val credentials = "${config.username}:${config.password}"
        authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        // Initialize Retrofit
        // Ensure URL ends with /
        val baseUrl = if (config.couchDbUrl.endsWith("/")) config.couchDbUrl else "${config.couchDbUrl}/"
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(getUnsafeOkHttpClient()) // Use unsafe client to bypass SSL errors
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(CouchDbApi::class.java)
    }

    fun logEvent(eventName: String, data: Map<String, Any>) {
        val config = currentConfig ?: run {
            Log.e(TAG, "Aura not initialized! Call setupExperiment first.")
            return
        }

        val entry = LogEntry(
            experimentID = config.experimentID,
            userID = config.userID,
            condition = config.condition,
            eventName = eventName,
            timestamp = System.currentTimeMillis(),
            payload = data
        )

        Log.d(TAG, "Sending Event: $entry")

        api?.postLog(config.dbName, authHeader ?: "", entry)?.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Event sent successfully: ${response.body()}")
                } else {
                    Log.e(TAG, "Failed to send event: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                Log.e(TAG, "Network error sending event", t)
            }
        })
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
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
