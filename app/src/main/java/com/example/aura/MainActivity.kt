package com.example.aura

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aura.lib.Aura

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple Layout for Testing
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        val button = Button(this).apply {
            text = "Send Test Event"
        }
        layout.addView(button)
        setContentView(layout)
        
        // 1. Initialize Aura (REPLACE CREDENTIALS HERE!)
        val config = Aura.Config(
            experimentID = "Test_Experiment_1",
            condition = "Test_Condition",
            userID = "Tester_Alex",
            couchDbUrl = "https://couchdb.hci.uni-hannover.de",
            dbName = "aura",
            username = BuildConfig.COUCHDB_USER,
            password = BuildConfig.COUCHDB_PASSWORD
        )
        Aura.setupExperiment(config)

        // 2. Log Event on Click
        button.setOnClickListener {
            val eventData = mapOf(
                "click_time" to System.currentTimeMillis(),
                "button_label" to button.text.toString()
            )
            
            Aura.logEvent("test_button_clicked", eventData)
            
            Toast.makeText(this, "Event sent! Check Logcat.", Toast.LENGTH_SHORT).show()
        }
    }
}