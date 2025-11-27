package com.example.aura.lib.network

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface CouchDbApi {
    @POST("{dbName}")
    fun postLog(
        @Path("dbName") dbName: String,
        @Header("Authorization") authHeader: String,
        @Body logEntry: LogEntry
    ): Call<JsonObject>
}
