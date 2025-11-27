package com.example.aura

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aura.lib.Aura

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Experiment
        val config = Aura.Config(
            experimentID = "Exp_001",
            condition = "A",
            userID = "User_123"
        )
        Aura.setupExperiment(config)

        // Log a test event
        Aura.logEvent("app_started", mapOf("timestamp" to System.currentTimeMillis()))
    }
}