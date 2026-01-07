package com.example.aura.lib.network

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface CouchDbApi {
    // Upload a log entry
    @POST("{dbName}")
    fun postLog(
        @Path("dbName") dbName: String,
        @Header("Authorization") authHeader: String,
        @Body logEntry: LogEntry
    ): Call<JsonObject>

    // Query documents using CouchDB Mango query (bidirectional: Server → App)
    @POST("{dbName}/_find")
    fun findDocuments(
        @Path("dbName") dbName: String,
        @Header("Authorization") authHeader: String,
        @Body query: MangoQuery
    ): Call<FindResponse>
}

/**
 * CouchDB Mango Query structure
 * Example: { "selector": { "user_id": "1", "event_name": "condition_started" } }
 */
data class MangoQuery(
    @SerializedName("selector") val selector: Map<String, Any>,
    @SerializedName("fields") val fields: List<String>? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("sort") val sort: List<Map<String, String>>? = null
)

/**
 * Response from CouchDB _find endpoint
 */
data class FindResponse(
    @SerializedName("docs") val docs: List<LogEntry>,
    @SerializedName("bookmark") val bookmark: String? = null,
    @SerializedName("warning") val warning: String? = null
)
