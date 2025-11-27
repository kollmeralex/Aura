package com.example.aura.lib

import android.util.Log

object Aura {
    private const val TAG = "AuraLib"

    data class Config(
        val experimentID: String,
        val condition: String,
        val userID: String
    )

    fun setupExperiment(config: Config) {
        Log.d(TAG, "Setup Experiment: $config")
    }

    fun logEvent(eventName: String, data: Map<String, Any>) {
        Log.d(TAG, "Log Event: $eventName, Data: $data")
    }
}
