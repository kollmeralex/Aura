package com.example.aura

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.example.aura.lib.Aura
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var userIdText: TextView
    private lateinit var conditionText: TextView
    private lateinit var userIdInput: TextInputEditText
    private lateinit var setupButton: MaterialButton
    private lateinit var experimentContainer: FrameLayout
    private lateinit var instructionText: TextView
    private lateinit var nextConditionButton: MaterialButton
    private lateinit var viewLogsButton: MaterialButton

    private var currentConditionIndex = 0
    private var currentTrial = 0
    private var targetStartTime = 0L
    private val trialsPerCondition = 10
    private var currentTarget: MaterialButton? = null
    
    private val conditions = listOf(
        Condition("Small", 48),
        Condition("Medium", 96),
        Condition("Large", 144)
    )
    private var conditionOrder = listOf<String>()
    private var trialResults = mutableListOf<TrialData>()

    data class Condition(val name: String, val sizeDp: Int)
    data class TrialData(
        val condition: String,
        val trial: Int,
        val reactionTime: Long,
        val distance: Double,
        val targetSize: Int
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
        
        nextConditionButton.isEnabled = false
        viewLogsButton.isEnabled = true
        viewLogsButton.text = "View All Results"
    }

    private fun setupClickListeners() {
        setupButton.setOnClickListener {
            initializeExperiment()
        }
        
        nextConditionButton.setOnClickListener {
            startNextCondition()
        }
        
        viewLogsButton.setOnClickListener {
            if (conditionOrder.isEmpty()) {
                showAllUserResults()
            } else {
                viewExperimentLogs()
            }
        }
    }

    private fun initializeExperiment() {
        val userId = userIdInput.text.toString().ifBlank { 
            Toast.makeText(this, "Please enter a participant ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
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
            conditionOrder = Aura.getSuggestedConditionOrder()
            
            Aura.logEvent("experiment_started", mapOf(
                "participant_id" to userId,
                "condition_order" to conditionOrder.joinToString(","),
                "trials_per_condition" to trialsPerCondition,
                "device" to android.os.Build.MODEL
            ))
            
            userIdText.text = "Participant: $userId"
            userIdInput.isEnabled = false
            setupButton.isEnabled = false
            nextConditionButton.isEnabled = true
            viewLogsButton.text = "Info"
            viewLogsButton.isEnabled = true
            
            instructionText.text = "Ready to start\n\nCondition order: ${conditionOrder.joinToString(" -> ")}"
            
            Toast.makeText(this, "Experiment initialized", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startNextCondition() {
        if (currentConditionIndex >= conditionOrder.size) {
            finishExperiment()
            return
        }
        
        val conditionName = conditionOrder[currentConditionIndex]
        val condition = conditions.find { it.name == conditionName } ?: return
        
        Aura.setCondition(conditionName)
        
        Aura.logEvent("condition_started", mapOf(
            "condition" to conditionName,
            "target_size_dp" to condition.sizeDp,
            "condition_index" to currentConditionIndex
        ))
        
        conditionText.text = "Condition: $conditionName (${condition.sizeDp}dp)"
        currentTrial = 0
        trialResults.clear()
        
        showConditionInstruction(condition)
    }

    private fun showConditionInstruction(condition: Condition) {
        AlertDialog.Builder(this)
            .setTitle("${condition.name} Targets")
            .setMessage(
                "Target size: ${condition.sizeDp}dp\n\n" +
                "Instructions:\n" +
                "- Tap targets as quickly as possible\n" +
                "- Complete $trialsPerCondition trials\n" +
                "- Try to be fast and accurate\n\n" +
                "Ready?"
            )
            .setPositiveButton("Start") { _, _ ->
                startTrials(condition)
            }
            .setCancelable(false)
            .show()
    }

    private fun startTrials(condition: Condition) {
        nextConditionButton.isEnabled = false
        experimentContainer.removeAllViews()
        showNextTarget(condition)
    }

    private fun showNextTarget(condition: Condition) {
        if (currentTrial >= trialsPerCondition) {
            finishCondition()
            return
        }
        
        currentTrial++
        currentTarget?.let { experimentContainer.removeView(it) }
        
        val sizePx = (condition.sizeDp * resources.displayMetrics.density).toInt()
        val target = MaterialButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            text = "$currentTrial"
            textSize = 14f
            cornerRadius = sizePx / 2
            setBackgroundColor(getTargetColor())
            elevation = 4f
        }
        
        val maxX = experimentContainer.width - sizePx
        val maxY = experimentContainer.height - sizePx
        val randomX = Random.nextInt(0, maxX.coerceAtLeast(1))
        val randomY = Random.nextInt(0, maxY.coerceAtLeast(1))
        
        target.x = randomX.toFloat()
        target.y = randomY.toFloat()
        
        val distance = currentTarget?.let {
            val dx = randomX.toDouble() - it.x.toDouble()
            val dy = randomY.toDouble() - it.y.toDouble()
            sqrt(dx.pow(2) + dy.pow(2))
        } ?: 0.0
        
        targetStartTime = System.currentTimeMillis()
        
        target.setOnClickListener {
            val reactionTime = System.currentTimeMillis() - targetStartTime
            
            trialResults.add(TrialData(
                condition = condition.name,
                trial = currentTrial,
                reactionTime = reactionTime,
                distance = distance,
                targetSize = condition.sizeDp
            ))
            
            Aura.logEvent("target_hit", mapOf(
                "condition" to condition.name,
                "trial" to currentTrial,
                "reaction_time_ms" to reactionTime,
                "distance_px" to distance,
                "target_size_dp" to condition.sizeDp,
                "index_of_difficulty" to calculateID(distance, sizePx.toDouble())
            ))
            
            target.setBackgroundColor(Color.parseColor("#4CAF50"))
            
            ObjectAnimator.ofFloat(target, "alpha", 1f, 0f).apply {
                duration = 150
                doOnEnd { showNextTarget(condition) }
                start()
            }
        }
        
        currentTarget = target
        experimentContainer.addView(target)
        
        target.alpha = 0f
        ObjectAnimator.ofFloat(target, "alpha", 0f, 1f).apply {
            duration = 200
            start()
        }
    }

    private fun calculateID(distance: Double, targetSize: Double): Double {
        return kotlin.math.log2((distance / targetSize) + 1)
    }

    private fun getTargetColor(): Int {
        val colors = listOf("#1976D2", "#D32F2F", "#388E3C", "#F57C00", "#7B1FA2", "#0097A7")
        return Color.parseColor(colors.random())
    }

    private fun finishCondition() {
        experimentContainer.removeAllViews()
        
        val avgTime = trialResults.map { it.reactionTime }.average()
        val conditionName = conditionOrder[currentConditionIndex]
        
        Aura.logEvent("condition_completed", mapOf(
            "condition" to conditionName,
            "avg_reaction_time_ms" to avgTime,
            "total_trials" to trialResults.size
        ))
        
        val resultsText = "Condition: $conditionName\n" +
                         "Trials: ${trialResults.size}\n" +
                         "Avg Time: ${avgTime.toInt()}ms"
        
        instructionText.text = resultsText
        
        AlertDialog.Builder(this)
            .setTitle("Condition Complete")
            .setMessage(resultsText)
            .setPositiveButton("Continue") { _, _ ->
                currentConditionIndex++
                if (currentConditionIndex < conditionOrder.size) {
                    instructionText.text = "Click 'Next Condition' to continue"
                    nextConditionButton.isEnabled = true
                } else {
                    finishExperiment()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun finishExperiment() {
        Aura.logEvent("experiment_completed", mapOf(
            "total_conditions" to conditionOrder.size,
            "total_trials" to (conditionOrder.size * trialsPerCondition)
        ))
        
        instructionText.text = "Experiment complete\n\nThank you for participating!"
        nextConditionButton.isEnabled = false
        
        AlertDialog.Builder(this)
            .setTitle("Experiment Complete")
            .setMessage(
                "Thank you for participating!\n\n" +
                "All data has been logged.\n" +
                "Total trials: ${conditionOrder.size * trialsPerCondition}"
            )
            .setPositiveButton("Start New Experiment") { _, _ ->
                resetExperiment()
            }
            .setNegativeButton("View Results") { _, _ ->
                showAllResults()
            }
            .setCancelable(false)
            .show()
    }

    private fun resetExperiment() {
        currentConditionIndex = 0
        currentTrial = 0
        conditionOrder = emptyList()
        trialResults.clear()
        
        userIdInput.text?.clear()
        userIdInput.isEnabled = true
        setupButton.isEnabled = true
        nextConditionButton.isEnabled = false
        viewLogsButton.text = "View All Results"
        viewLogsButton.isEnabled = true
        
        userIdText.text = "Participant: Not initialized"
        conditionText.text = "Condition: None"
        instructionText.text = "Initialize experiment to begin"
        experimentContainer.removeAllViews()
        
        Toast.makeText(this, "Ready for new experiment", Toast.LENGTH_SHORT).show()
    }

    private fun showAllResults() {
        val allTrials = mutableListOf<String>()
        
        conditions.forEach { cond ->
            val condTrials = trialResults.filter { it.condition == cond.name }
            if (condTrials.isNotEmpty()) {
                val avgTime = condTrials.map { it.reactionTime }.average()
                allTrials.add("${cond.name}: ${avgTime.toInt()}ms avg (${condTrials.size} trials)")
            }
        }
        
        val resultsText = if (allTrials.isEmpty()) {
            "No data recorded yet"
        } else {
            "Results:\n\n" + allTrials.joinToString("\n")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Experiment Results")
            .setMessage(resultsText)
            .setPositiveButton("OK") { _, _ ->
                resetExperiment()
            }
            .show()
    }

    private fun viewExperimentLogs() {
        val message = if (conditionOrder.isEmpty()) {
            "Experiment not initialized yet.\n\n" +
            "Initialize an experiment first to see details."
        } else {
            "Study: Fitts' Law\n\n" +
            "Counterbalancing:\n" +
            "- Odd IDs (1,3,5...): ${conditions.map { it.name }.joinToString(" -> ")}\n" +
            "- Even IDs (2,4,6...): ${conditions.map { it.name }.reversed().joinToString(" -> ")}\n\n" +
            "Your order: ${conditionOrder.joinToString(" -> ")}\n" +
            "Trials per condition: $trialsPerCondition\n\n" +
            "Data logged:\n" +
            "- experiment_started\n" +
            "- condition_started\n" +
            "- target_hit (per trial)\n" +
            "- condition_completed\n" +
            "- experiment_completed\n\n" +
            "Logged to: CouchDB + local JSONL files"
        }
        
        AlertDialog.Builder(this)
            .setTitle("Experiment Info")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAllUserResults() {
        Toast.makeText(this, "Loading user data...", Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                val response = com.example.aura.lib.Aura.executeRawQuery(
                    """
                    {
                        "selector": {
                            "experiment_id": "Fitts_Law_Exp",
                            "event_name": "experiment_started"
                        },
                        "fields": ["user_id"],
                        "limit": 1000
                    }
                    """.trimIndent()
                )
                
                runOnUiThread {
                    if (response != null) {
                        parseAndShowUserIds(response)
                    } else {
                        Toast.makeText(this, "No data found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun parseAndShowUserIds(jsonResponse: String) {
        try {
            val userIds = mutableSetOf<String>()
            
            val docsStart = jsonResponse.indexOf("\"docs\":[")
            if (docsStart != -1) {
                val docsSection = jsonResponse.substring(docsStart)
                val userIdPattern = "\"user_id\":\"([^\"]+)\"".toRegex()
                
                userIdPattern.findAll(docsSection).forEach { match ->
                    userIds.add(match.groupValues[1])
                }
            }
            
            if (userIds.isEmpty()) {
                Toast.makeText(this, "No participants found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val userIdArray = userIds.sorted().toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("Select Participant")
                .setItems(userIdArray) { _, which ->
                    loadUserResults(userIdArray[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            Toast.makeText(this, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadUserResults(userId: String) {
        Toast.makeText(this, "Loading data for user $userId...", Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                val response = com.example.aura.lib.Aura.executeRawQuery(
                    """
                    {
                        "selector": {
                            "experiment_id": "Fitts_Law_Exp",
                            "user_id": "$userId",
                            "event_name": "target_hit"
                        },
                        "limit": 100000
                    }
                    """.trimIndent()
                )
                
                runOnUiThread {
                    if (response != null) {
                        displayUserResults(userId, response)
                    } else {
                        Toast.makeText(this, "No data found for user $userId", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun displayUserResults(userId: String, jsonResponse: String) {
        try {
            val conditionTimes = mutableMapOf<String, MutableList<Long>>()
            
            val conditionPattern = "\"condition\":\"([^\"]+)\"".toRegex()
            val reactionTimePattern = "\"reaction_time_ms\":(\\d+)".toRegex()
            
            val conditions = conditionPattern.findAll(jsonResponse).map { it.groupValues[1] }.toList()
            val times = reactionTimePattern.findAll(jsonResponse).map { it.groupValues[1].toLong() }.toList()
            
            conditions.forEachIndexed { index, condition ->
                if (index < times.size) {
                    conditionTimes.getOrPut(condition) { mutableListOf() }.add(times[index])
                }
            }
            
            val resultsText = buildString {
                append("Participant: $userId\n\n")
                
                if (conditionTimes.isEmpty()) {
                    append("No trial data found")
                } else {
                    conditionTimes.entries.sortedBy { it.key }.forEach { (condition, times) ->
                        val avgTime = times.average()
                        val minTime = times.minOrNull() ?: 0
                        val maxTime = times.maxOrNull() ?: 0
                        
                        append("$condition (${times.size} trials):\n")
                        append("  Avg: ${avgTime.toInt()}ms\n")
                        append("  Min: ${minTime}ms\n")
                        append("  Max: ${maxTime}ms\n\n")
                    }
                    
                    val allTimes = conditionTimes.values.flatten()
                    append("Overall:\n")
                    append("  Total trials: ${allTimes.size}\n")
                    append("  Avg: ${allTimes.average().toInt()}ms")
                }
            }
            
            AlertDialog.Builder(this)
                .setTitle("Results: User $userId")
                .setMessage(resultsText)
                .setPositiveButton("OK", null)
                .show()
                
        } catch (e: Exception) {
            Toast.makeText(this, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
