package com.example.aura

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aura.lib.Aura

/**
 * Demo App für die AURA Logging-Bibliothek v1.1.0
 * 
 * Diese App testet ALLE Features über JitPack:
 * - setupExperiment() mit availableConditions
 * - logEvent() mit Payload
 * - setCondition()
 * - getSuggestedConditionOrder() (Counterbalancing)
 * - getCompletedConditions() (Bidirektional: Server → App)
 * - getServerAwareConditionOrder() (Smart Counterbalancing)
 * 
 * implementation("com.github.kollmeralex:Aura:v1.1.0")
 */
class MainActivity : AppCompatActivity() {

    private var eventCount = 0
    private lateinit var statusTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var userIdInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        val scrollView = ScrollView(this)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        val titleTextView = TextView(this).apply {
            text = "🔬 AURA v1.1.0 Feature Test"
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#6200EE"))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(titleTextView)

        // Subtitle
        val subtitleTextView = TextView(this).apply {
            text = "via JitPack: com.github.kollmeralex:Aura:v1.1.0"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(subtitleTextView)

        // User ID Input
        val userIdLabel = TextView(this).apply {
            text = "User ID:"
            textSize = 14f
        }
        layout.addView(userIdLabel)

        userIdInput = EditText(this).apply {
            hint = "z.B. 1, 2, 3..."
            setText("1")
        }
        layout.addView(userIdInput)

        // Status display
        statusTextView = TextView(this).apply {
            text = "Status: Nicht initialisiert"
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 16, 0, 16)
        }
        layout.addView(statusTextView)

        // ===== BUTTON 1: Setup Experiment =====
        val setupButton = Button(this).apply {
            text = "1️⃣ setupExperiment()"
            setOnClickListener { testSetupExperiment() }
        }
        layout.addView(setupButton)

        // ===== BUTTON 2: Log Event =====
        val logEventButton = Button(this).apply {
            text = "2️⃣ logEvent()"
            setOnClickListener { testLogEvent() }
        }
        layout.addView(logEventButton)

        // ===== BUTTON 3: Set Condition =====
        val setConditionButton = Button(this).apply {
            text = "3️⃣ setCondition()"
            setOnClickListener { testSetCondition() }
        }
        layout.addView(setConditionButton)

        // ===== BUTTON 4: Get Suggested Order (Local) =====
        val suggestedOrderButton = Button(this).apply {
            text = "4️⃣ getSuggestedConditionOrder()"
            setOnClickListener { testGetSuggestedConditionOrder() }
        }
        layout.addView(suggestedOrderButton)

        // ===== BUTTON 5: Get Completed Conditions (Server) =====
        val completedConditionsButton = Button(this).apply {
            text = "5️⃣ getCompletedConditions() [Server]"
            setOnClickListener { testGetCompletedConditions() }
        }
        layout.addView(completedConditionsButton)

        // ===== BUTTON 6: Get Server-Aware Order =====
        val serverAwareButton = Button(this).apply {
            text = "6️⃣ getServerAwareConditionOrder()"
            setOnClickListener { testGetServerAwareConditionOrder() }
        }
        layout.addView(serverAwareButton)

        // ===== Log Output Area =====
        val logLabel = TextView(this).apply {
            text = "📋 Log Output:"
            textSize = 14f
            setPadding(0, 32, 0, 8)
        }
        layout.addView(logLabel)

        logTextView = TextView(this).apply {
            text = ""
            textSize = 12f
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            setPadding(16, 16, 16, 16)
            minHeight = 300
        }
        layout.addView(logTextView)

        // Clear Log Button
        val clearButton = Button(this).apply {
            text = "🗑️ Clear Log"
            setOnClickListener { 
                logTextView.text = ""
                eventCount = 0
            }
        }
        layout.addView(clearButton)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    // ==================== TEST FUNCTIONS ====================

    private fun testSetupExperiment() {
        val userId = userIdInput.text.toString().ifBlank { "1" }
        
        try {
            val config = Aura.Config(
                context = applicationContext,
                experimentID = "JitPack_v1.1.0_Test",
                userID = userId,
                couchDbUrl = "https://couchdb.hci.uni-hannover.de",
                dbName = "aura",
                username = BuildConfig.COUCHDB_USER,
                password = BuildConfig.COUCHDB_PASSWORD,
                availableConditions = listOf("ConditionA", "ConditionB", "ConditionC")
            )
            
            Aura.setupExperiment(config)
            
            statusTextView.text = "✅ Status: Initialisiert (User: $userId)"
            appendLog("✅ setupExperiment() erfolgreich!")
            appendLog("   - ExperimentID: JitPack_v1.1.0_Test")
            appendLog("   - UserID: $userId")
            appendLog("   - Conditions: A, B, C")
            
            Toast.makeText(this, "AURA initialisiert!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusTextView.text = "❌ Status: Fehler"
            appendLog("❌ setupExperiment() FEHLER: ${e.message}")
            Log.e("AURA_TEST", "Setup failed", e)
        }
    }

    private fun testLogEvent() {
        eventCount++
        
        try {
            val payload = mapOf(
                "event_number" to eventCount,
                "test_type" to "jitpack_v1.1.0",
                "timestamp" to System.currentTimeMillis(),
                "device" to android.os.Build.MODEL
            )
            
            Aura.logEvent("test_event", payload)
            
            appendLog("✅ logEvent() #$eventCount gesendet!")
            appendLog("   - Payload: $payload")
            
            Toast.makeText(this, "Event #$eventCount logged!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            appendLog("❌ logEvent() FEHLER: ${e.message}")
            Log.e("AURA_TEST", "LogEvent failed", e)
        }
    }

    private fun testSetCondition() {
        try {
            val conditions = listOf("ConditionA", "ConditionB", "ConditionC")
            val randomCondition = conditions.random()
            
            Aura.setCondition(randomCondition)
            
            appendLog("✅ setCondition('$randomCondition') erfolgreich!")
            
            Toast.makeText(this, "Condition: $randomCondition", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            appendLog("❌ setCondition() FEHLER: ${e.message}")
            Log.e("AURA_TEST", "SetCondition failed", e)
        }
    }

    private fun testGetSuggestedConditionOrder() {
        try {
            val order = Aura.getSuggestedConditionOrder()
            
            appendLog("✅ getSuggestedConditionOrder():")
            appendLog("   - Reihenfolge: $order")
            appendLog("   - (Basiert auf UserID für Counterbalancing)")
            
            Toast.makeText(this, "Order: $order", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            appendLog("❌ getSuggestedConditionOrder() FEHLER: ${e.message}")
            Log.e("AURA_TEST", "GetSuggestedOrder failed", e)
        }
    }

    private fun testGetCompletedConditions() {
        appendLog("⏳ getCompletedConditions() - Lade vom Server...")
        
        try {
            Aura.getCompletedConditions(
                onSuccess = { completedConditions ->
                    runOnUiThread {
                        appendLog("✅ getCompletedConditions() [BIDIREKTIONAL]:")
                        if (completedConditions.isEmpty()) {
                            appendLog("   - Keine Conditions abgeschlossen")
                        } else {
                            appendLog("   - Abgeschlossen: $completedConditions")
                        }
                        Toast.makeText(this, "Completed: $completedConditions", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        appendLog("❌ getCompletedConditions() FEHLER: ${error.message}")
                    }
                }
            )
        } catch (e: Exception) {
            appendLog("❌ getCompletedConditions() FEHLER: ${e.message}")
            Log.e("AURA_TEST", "GetCompletedConditions failed", e)
        }
    }

    private fun testGetServerAwareConditionOrder() {
        appendLog("⏳ getServerAwareConditionOrder() - Lade vom Server...")
        
        try {
            Aura.getServerAwareConditionOrder(
                onSuccess = { remainingConditions ->
                    runOnUiThread {
                        appendLog("✅ getServerAwareConditionOrder() [SMART]:")
                        if (remainingConditions.isEmpty()) {
                            appendLog("   - Alle Conditions abgeschlossen! 🎉")
                        } else {
                            appendLog("   - Verbleibend: $remainingConditions")
                        }
                        Toast.makeText(this, "Remaining: $remainingConditions", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        appendLog("⚠️ getServerAwareConditionOrder() Fallback (offline):")
                        appendLog("   - ${error.message}")
                    }
                }
            )
        } catch (e: Exception) {
            appendLog("❌ getServerAwareConditionOrder() FEHLER: ${e.message}")
            Log.e("AURA_TEST", "GetServerAwareOrder failed", e)
        }
    }

    // ==================== HELPER ====================

    private fun appendLog(message: String) {
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        logTextView.append("[$currentTime] $message\n")
    }

    override fun onPause() {
        super.onPause()
        try {
            Aura.logEvent("app_paused", emptyMap())
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        try {
            Aura.logEvent("app_resumed", emptyMap())
        } catch (_: Exception) { }
    }
}