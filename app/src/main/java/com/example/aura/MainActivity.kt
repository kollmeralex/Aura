package com.example.aura

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.example.aura.lib.Aura
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fitts' Law Experiment App powered by AURA v1.1.1
 * 
 * A realistic HCI research experiment demonstrating:
 * - Three target size conditions (Small: 48dp, Medium: 96dp, Large: 144dp)
 * - Reaction time and accuracy measurement
 * - Counterbalanced condition order
 * - Automatic logging to CouchDB via AURA
 * 
 * Implementation: com.github.kollmeralex:Aura:v1.1.1
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var userIdText: TextView
    private lateinit var conditionText: TextView
    private lateinit var userIdInput: TextInputEditText
    private lateinit var setupButton: MaterialButton
    private lateinit var experimentContainer: FrameLayout
    private lateinit var instructionText: TextView
    private lateinit var nextConditionButton: MaterialButton
    private lateinit var viewLogsButton: MaterialButton

    // Experiment State
    private var isExperimentRunning = false
    private var currentConditionIndex = 0
    private var currentTrial = 0
    private var targetStartTime = 0L
    private var totalTrialsPerCondition = 10
    private var currentTargetButton: MaterialButton? = null
    
    // Condition Definitions
    private val conditions = listOf(
        TargetCondition("Small", 48),
        TargetCondition("Medium", 96),
        TargetCondition("Large", 144)
    )
    private var conditionOrder = listOf<String>()

    // Metrics
    private var trialResults = mutableListOf<TrialResult>()

    data class TargetCondition(val name: String, val sizeDp: Int)
    data class TrialResult(
        val condition: String,
        val trialNumber: Int,
        val reactionTimeMs: Long,
        val distance: Double,
        val targetSize: Int,
        val accuracy: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        userIdText = findViewById(R.id.userIdText)
        conditionText = findViewById(R.id.conditionText)
        userIdInput = findViewById(R.id.userIdInput)
        setupButton = findViewById(R.id.setupButton)
        experimentContainer = findViewById(R.id.experimentContainer)
        instructionText = findViewById(R.id.instructionText)
        nextConditionButton = findViewById(R.id.nextConditionButton)
        viewLogsButton = findViewById(R.id.viewLogsButton)
        
        // Initially disable experiment controls
        nextConditionButton.isEnabled = false
        viewLogsButton.isEnabled = false
    }

    private fun setupClickListeners() {
        setupButton.setOnClickListener {
            initializeExperiment()
        }
        
        nextConditionButton.setOnClickListener {
            startNextCondition()
        }
        
        viewLogsButton.setOnClickListener {
            viewExperimentLogs()
        }
    }

    private fun initializeExperiment() {
        val userId = userIdInput.text.toString().ifBlank { 
            Toast.makeText(this, "Please enter a Participant ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Setup AURA with three target size conditions
            val config = Aura.Config(
                context = applicationContext,
                experimentID = "Fitts_Law_Exp",
                userID = userId,
                couchDbUrl = "https://couchdb.hci.uni-hannover.de",
                dbName = "aura",
                username = BuildConfig.COUCHDB_USER,
                password = BuildConfig.COUCHDB_PASSWORD,
                availableConditions = conditions.map { it.name }
            )
            
            Aura.setupExperiment(config)
            
            // Get counterbalanced condition order
            conditionOrder = Aura.getSuggestedConditionOrder()
            
            // Log experiment start
            Aura.logEvent("experiment_started", mapOf(
                "participant_id" to userId,
                "condition_order" to conditionOrder.joinToString(","),
                "trials_per_condition" to totalTrialsPerCondition,
                "device" to android.os.Build.MODEL,
                "android_version" to android.os.Build.VERSION.RELEASE
            ))
            
            // Update UI
            userIdText.text = "Participant ID: $userId"
            userIdInput.isEnabled = false
            setupButton.isEnabled = false
            nextConditionButton.isEnabled = true
            viewLogsButton.isEnabled = true
            
            instructionText.text = "✅ Ready! Click 'Next Condition' to start\n\nCondition Order: ${conditionOrder.joinToString(" → ")}"
            
            Toast.makeText(this, "Experiment initialized! Order: $conditionOrder", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startNextCondition() {
        if (currentConditionIndex >= conditionOrder.size) {
            // Experiment complete
            finishExperiment()
            return
        }
        
        val conditionName = conditionOrder[currentConditionIndex]
        val condition = conditions.find { it.name == conditionName } ?: return
        
        // Set condition in AURA
        Aura.setCondition(conditionName)
        
        // Log condition start
        Aura.logEvent("condition_started", mapOf(
            "condition" to conditionName,
            "target_size_dp" to condition.sizeDp,
            "condition_index" to currentConditionIndex
        ))
        
        // Update UI
        conditionText.text = "Current Condition: $conditionName (${condition.sizeDp}dp)"
        currentTrial = 0
        trialResults.clear()
        
        // Show instruction
        showConditionInstruction(condition)
    }

    private fun showConditionInstruction(condition: TargetCondition) {
        AlertDialog.Builder(this)
            .setTitle("🎯 ${condition.name} Target Condition")
            .setMessage(
                "Target Size: ${condition.sizeDp}dp\n\n" +
                "Instructions:\n" +
                "• Tap the colored targets as quickly as possible\n" +
                "• Complete $totalTrialsPerCondition trials\n" +
                "• Try to be both fast AND accurate\n\n" +
                "Ready to start?"
            )
            .setPositiveButton("Start") { _, _ ->
                startTrials(condition)
            }
            .setCancelable(false)
            .show()
    }

    private fun startTrials(condition: TargetCondition) {
        isExperimentRunning = true
        nextConditionButton.isEnabled = false
        experimentContainer.removeAllViews()
        
        showNextTarget(condition)
    }

    private fun showNextTarget(condition: TargetCondition) {
        if (currentTrial >= totalTrialsPerCondition) {
            // Condition complete
            finishCondition()
            return
        }
        
        currentTrial++
        
        // Remove previous target
        currentTargetButton?.let { experimentContainer.removeView(it) }
        
        // Create new target button
        val sizePx = (condition.sizeDp * resources.displayMetrics.density).toInt()
        val target = MaterialButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            text = "$currentTrial"
            textSize = 16f
            cornerRadius = sizePx / 2
            setBackgroundColor(getRandomColor())
            elevation = 8f
        }
        
        // Random position
        val maxX = experimentContainer.width - sizePx
        val maxY = experimentContainer.height - sizePx
        val randomX = Random.nextInt(0, maxX.coerceAtLeast(1))
        val randomY = Random.nextInt(0, maxY.coerceAtLeast(1))
        
        target.x = randomX.toFloat()
        target.y = randomY.toFloat()
        
        // Calculate distance from previous target (Fitts' Law parameter)
        val distance = currentTargetButton?.let {
            val dx = randomX - it.x
            val dy = randomY - it.y
            sqrt(dx.pow(2) + dy.pow(2))
        } ?: 0.0
        
        targetStartTime = System.currentTimeMillis()
        
        target.setOnClickListener {
            val reactionTime = System.currentTimeMillis() - targetStartTime
            
            // Record trial result
            val result = TrialResult(
                condition = condition.name,
                trialNumber = currentTrial,
                reactionTimeMs = reactionTime,
                distance = distance,
                targetSize = condition.sizeDp,
                accuracy = true // Hit
            )
            trialResults.add(result)
            
            // Log to AURA
            Aura.logEvent("target_hit", mapOf(
                "condition" to condition.name,
                "trial" to currentTrial,
                "reaction_time_ms" to reactionTime,
                "distance_px" to distance,
                "target_size_dp" to condition.sizeDp,
                "target_size_px" to sizePx,
                "index_of_difficulty" to calculateIndexOfDifficulty(distance, sizePx.toDouble()),
                "accuracy" to "hit"
            ))
            
            // Visual feedback
            target.setBackgroundColor(Color.parseColor("#4CAF50"))
            
            // Animate and show next
            ObjectAnimator.ofFloat(target, "alpha", 1f, 0f).apply {
                duration = 200
                doOnEnd { showNextTarget(condition) }
                start()
            }
        }
        
        currentTargetButton = target
        experimentContainer.addView(target)
        
        // Fade in animation
        target.alpha = 0f
        ObjectAnimator.ofFloat(target, "alpha", 0f, 1f).apply {
            duration = 300
            start()
        }
    }

    private fun calculateIndexOfDifficulty(distance: Double, targetSize: Double): Double {
        // Fitts' Law: ID = log2(D/W + 1)
        return kotlin.math.log2((distance / targetSize) + 1)
    }

    private fun getRandomColor(): Int {
        val colors = listOf(
            "#2196F3", "#FF5722", "#4CAF50", "#FF9800", 
            "#9C27B0", "#00BCD4", "#FFEB3B", "#E91E63"
        )
        return Color.parseColor(colors.random())
    }

    private fun finishCondition() {
        isExperimentRunning = false
        experimentContainer.removeAllViews()
        
        // Calculate statistics
        val avgReactionTime = trialResults.map { it.reactionTimeMs }.average()
        val accuracy = (trialResults.count { it.accuracy }.toDouble() / trialResults.size) * 100
        
        val conditionName = conditionOrder[currentConditionIndex]
        
        // Log condition completion
        Aura.logEvent("condition_completed", mapOf(
            "condition" to conditionName,
            "avg_reaction_time_ms" to avgReactionTime,
            "accuracy_percent" to accuracy,
            "total_trials" to trialResults.size
        ))
        
        // Show results
        val resultsText = """
            ✅ Condition Complete!
            
            Condition: $conditionName
            Trials: ${trialResults.size}
            Avg Reaction Time: ${avgReactionTime.toInt()}ms
            Accuracy: ${accuracy.toInt()}%
        """.trimIndent()
        
        instructionText.text = resultsText
        
        AlertDialog.Builder(this)
            .setTitle("📊 Condition Results")
            .setMessage(resultsText)
            .setPositiveButton("Continue") { _, _ ->
                currentConditionIndex++
                if (currentConditionIndex < conditionOrder.size) {
                    instructionText.text = "👆 Click 'Next Condition' to continue"
                    nextConditionButton.isEnabled = true
                } else {
                    finishExperiment()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun finishExperiment() {
        // Log experiment completion
        Aura.logEvent("experiment_completed", mapOf(
            "total_conditions" to conditionOrder.size,
            "total_trials" to (conditionOrder.size * totalTrialsPerCondition)
        ))
        
        instructionText.text = """
            🎉 Experiment Complete!
            
            Thank you for participating!
            All data has been logged to CouchDB.
            
            You can close the app now.
        """.trimIndent()
        
        nextConditionButton.isEnabled = false
        
        Toast.makeText(this, "Experiment finished! Thank you!", Toast.LENGTH_LONG).show()
    }

    private fun viewExperimentLogs() {
        AlertDialog.Builder(this)
            .setTitle("📋 Experiment Info")
            .setMessage(
                "Experiment: Fitts' Law\n" +
                "Conditions: ${conditions.map { it.name }.joinToString(", ")}\n" +
                "Order: ${conditionOrder.joinToString(" → ")}\n" +
                "Trials per condition: $totalTrialsPerCondition\n\n" +
                "All data is automatically logged to:\n" +
                "https://couchdb.hci.uni-hannover.de/aura\n\n" +
                "Logged events:\n" +
                "• experiment_started\n" +
                "• condition_started\n" +
                "• target_hit (per trial)\n" +
                "• condition_completed\n" +
                "• experiment_completed"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
