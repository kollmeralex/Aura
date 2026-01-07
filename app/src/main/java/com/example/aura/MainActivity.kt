package com.example.aura

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aura.lib.Aura

class MainActivity : AppCompatActivity() {
    
    private lateinit var infoText: TextView
    private lateinit var startButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple UI for Experiment Setup
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }
        
        val title = TextView(this).apply {
            text = "AURA Experiment Setup"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val userIdInput = EditText(this).apply {
            hint = "User ID (e.g. 1, 2)"
            setText("1")
        }
        
        infoText = TextView(this).apply {
            text = "Initialize to see condition order."
            setPadding(0, 32, 0, 32)
        }

        startButton = Button(this).apply {
            text = "Start Experiment"
            isEnabled = false
        }

        val initButton = Button(this).apply {
            text = "Initialize Aura & Counterbalancing"
        }

        layout.addView(title)
        layout.addView(userIdInput)
        layout.addView(initButton)
        layout.addView(infoText)
        layout.addView(startButton)
        setContentView(layout)

        // Logic
        initButton.setOnClickListener {
            val userId = userIdInput.text.toString()
            if (userId.isBlank()) {
                Toast.makeText(this, "Please enter a User ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Setup Aura with required conditions
            val config = Aura.Config(
                context = applicationContext,
                experimentID = "Fitts_Law_Exp",
                userID = userId,
                couchDbUrl = "https://couchdb.hci.uni-hannover.de",
                dbName = "aura",
                username = BuildConfig.COUCHDB_USER,
                password = BuildConfig.COUCHDB_PASSWORD,
                availableConditions = listOf("RightHand", "LeftHand") 
            )
            Aura.setupExperiment(config)

            // 2. Get Server-Aware Counterbalanced Order (NEW: Bidirectional!)
            infoText.text = "Loading from server..."
            initButton.isEnabled = false
            
            Aura.getServerAwareConditionOrder(
                onSuccess = { remainingConditions ->
                    runOnUiThread {
                        if (remainingConditions.isEmpty()) {
                            infoText.text = "User: $userId\n\n✅ All conditions completed!"
                            startButton.text = "Experiment Done"
                            startButton.isEnabled = false
                        } else {
                            val allConditions = Aura.getSuggestedConditionOrder()
                            val completed = allConditions.filter { it !in remainingConditions }
                            
                            infoText.text = buildString {
                                append("User: $userId\n\n")
                                if (completed.isNotEmpty()) {
                                    append("✅ Completed: ${completed.joinToString()}\n")
                                }
                                append("📋 Remaining:\n")
                                remainingConditions.forEachIndexed { index, condition ->
                                    append("${index + 1}. $condition\n")
                                }
                            }
                            startButton.isEnabled = true
                        }
                        initButton.isEnabled = true
                    }
                },
                onError = { _ ->
                    runOnUiThread {
                        // Fallback already handled in Aura - this won't be called normally
                        Toast.makeText(this, "Using offline mode", Toast.LENGTH_SHORT).show()
                        initButton.isEnabled = true
                    }
                }
            )
        }

        startButton.setOnClickListener {
            val intent = Intent(this, FittsLawActivity::class.java)
            startActivity(intent)
        }
    }
}
