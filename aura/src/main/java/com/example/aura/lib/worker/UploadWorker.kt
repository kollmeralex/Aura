package com.example.aura.lib.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.aura.lib.network.CouchDbApi
import com.example.aura.lib.network.LogEntry
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UploadWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    private val gson = Gson()

    companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_AUTH_HEADER = "auth_header"
        const val KEY_DB_NAME = "db_name"
        private const val TAG = "AuraUploadWorker"
    }

    override fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.failure()
        val authHeader = inputData.getString(KEY_AUTH_HEADER) ?: return Result.failure()
        val dbName = inputData.getString(KEY_DB_NAME) ?: return Result.failure()

        // Ensure Base URL ends with /
        val finalBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val api = createApi(finalBaseUrl)

        val queueDir = applicationContext.getExternalFilesDir("aura_queue")
        if (queueDir == null || !queueDir.exists()) {
            return Result.success()
        }

        val files = queueDir.listFiles()
        if (files.isNullOrEmpty()) {
            return Result.success()
        }

        // Sort files to try and process older events first (if named by timestamp or simple order)
        files.sortBy { it.lastModified() }

        var allSuccess = true

        for (file in files) {
            if (!file.name.endsWith(".jsonl")) continue

            Log.d(TAG, "Processing queue file: ${file.name}")

            val lines = file.readLines()
            var fileSuccess = true

            for (line in lines) {
                if (line.isBlank()) continue
                try {
                    val entry = gson.fromJson(line, LogEntry::class.java)
                    
                    // Synchronous execution
                    val response = api.postLog(dbName, authHeader, entry).execute()

                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to upload event. Code: ${response.code()}, Message: ${response.message()}")
                        fileSuccess = false
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during upload", e)
                    fileSuccess = false
                    break
                }
            }

            if (fileSuccess) {
                // If all events in this file uploaded successfully, delete the file
                if (file.delete()) {
                    Log.d(TAG, "Successfully uploaded and deleted: ${file.name}")
                } else {
                    Log.e(TAG, "Failed to delete processed file: ${file.name}")
                    // This is weird, but we don't want to re-upload. 
                    // Maybe rename to .bak? For now, treat as success but log error.
                }
            } else {
                allSuccess = false
                Log.w(TAG, "Stopping upload batch due to error in file: ${file.name}")
                // Stop processing further files to maintain order if important, 
                // and back off via Retry
                break
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }

    private fun createApi(baseUrl: String): CouchDbApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CouchDbApi::class.java)
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
