package com.example.aura

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aura.lib.Aura

/**
 * Demo App für die AURA Logging-Bibliothek
 *
 * Diese App demonstriert die Verwendung von AURA über JitPack:
 * implementation("com.github.kollmeralex:Aura:v1.0.0")
 */
class MainActivity : AppCompatActivity() {

    private var eventCount = 0
    private lateinit var counterTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
        setupAura()

        // Log app start event
        logEvent("app_started")
    }

    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        // Title
        val titleTextView = TextView(this).apply {
            text = "Aura"
            textSize = 32f
            setTextColor(android.graphics.Color.parseColor("#6200EE"))
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleTextView)

        // Info text
        val infoTextView = TextView(this).apply {
            text = "AURA Logging Library Demo\n(via JitPack v1.0.0)"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(infoTextView)

        // Event counter
        counterTextView = TextView(this).apply {
            text = "Events logged: $eventCount"
            textSize = 18f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(counterTextView)

        // Send event button
        val sendButton = Button(this).apply {
            text = "SEND TEST EVENT"
            setOnClickListener { onSendEventClicked() }
        }
        layout.addView(sendButton)

        setContentView(layout)
    }

    private fun setupAura() {
        // Initialize AURA with CouchDB configuration
        // Credentials are injected from local.properties at build time
        val config = Aura.Config(
            experimentID = "AURA_Demo_v1.0.0",
            condition = "JitPack_Integration",
            userID = "Demo_User_${System.currentTimeMillis() % 1000}",
            couchDbUrl = "https://couchdb.hci.uni-hannover.de",
            dbName = "aura",
            username = BuildConfig.COUCHDB_USER,
            password = BuildConfig.COUCHDB_PASSWORD
        )

        try {
            Aura.setupExperiment(config)
            Log.d("AURA", "✓ AURA initialized successfully (JitPack v1.0.0)")
        } catch (e: Exception) {
            Log.e("AURA", "✗ Failed to initialize AURA: ${e.message}", e)
            Toast.makeText(this, "AURA initialization failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun onSendEventClicked() {
        eventCount++

        val eventData = mapOf(
            "event_number" to eventCount,
            "timestamp_ms" to System.currentTimeMillis(),
            "device_model" to android.os.Build.MODEL,
            "android_version" to android.os.Build.VERSION.SDK_INT,
            "source" to "jitpack_demo"
        )

        logEvent("demo_button_clicked", eventData)

        // Update UI
        counterTextView.text = "Events logged: $eventCount"

        // Show feedback
        Toast.makeText(
            this,
            "✓ Event #$eventCount logged to CouchDB",
            Toast.LENGTH_SHORT
        ).show()

        Log.d("AURA", "Event #$eventCount sent: demo_button_clicked")
    }

    /**
     * Helper function to log events with error handling
     */
    private fun logEvent(eventName: String, data: Map<String, Any> = emptyMap()) {
        try {
            Aura.logEvent(eventName, data)
            Log.d("AURA", "✓ Event logged: $eventName")
        } catch (e: Exception) {
            Log.e("AURA", "✗ Failed to log event '$eventName': ${e.message}", e)
            Toast.makeText(this, "Failed to log event", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        logEvent("app_paused")
    }

    override fun onResume() {
        super.onResume()
        logEvent("app_resumed")
    }
}