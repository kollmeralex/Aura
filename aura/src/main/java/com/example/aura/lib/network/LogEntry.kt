package com.example.aura.lib.network

import com.google.gson.annotations.SerializedName

data class LogEntry(
    @SerializedName("experiment_id") val experimentID: String,
    @SerializedName("user_id") val userID: String,
    @SerializedName("condition") val condition: String,
    @SerializedName("event_name") val eventName: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("payload") val payload: Map<String, Any>
)
