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
        initializeDatabaseConnection()
        setupClickListeners()
    }

    private fun initializeDatabaseConnection() {
        // Initialize database connection for querying results before experiment starts
        try {
            val dbConfig = Aura.DatabaseConfig(
                context = applicationContext,
                couchDbUrl = "https://couchdb.hci.uni-hannover.de",
                dbName = "aura",
                username = BuildConfig.COUCHDB_USER,
                password = BuildConfig.COUCHDB_PASSWORD
            )
            Aura.initializeDatabase(dbConfig)
        } catch (e: Exception) {
            // Ignore - will be initialized when experiment starts
        }
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
            val userId = userIdInput.text.toString().trim()
            if (userId.isBlank()) {
                Toast.makeText(this, "Please enter a participant ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Check if user ID already exists
            checkUserIdExists(userId) { exists, existingIds ->
                if (exists) {
                    showUserIdExistsDialog(userId, existingIds)
                } else {
                    initializeExperiment(userId)
                }
            }
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
    
    private fun checkUserIdExists(userId: String, callback: (exists: Boolean, existingIds: List<String>) -> Unit) {
        Toast.makeText(this, "Checking participant ID...", Toast.LENGTH_SHORT).show()
        
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
                
                val existingIds = mutableSetOf<String>()
                if (response != null) {
                    "\"user_id\":\\s*\"([^\"]+)\"".toRegex().findAll(response).forEach {
                        existingIds.add(it.groupValues[1])
                    }
                }
                
                val sortedIds = existingIds.sorted()
                val exists = userId in existingIds
                
                runOnUiThread {
                    callback(exists, sortedIds)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    // If check fails, proceed anyway
                    callback(false, emptyList())
                }
            }
        }.start()
    }
    
    private fun showUserIdExistsDialog(userId: String, existingIds: List<String>) {
        val context = this
        
        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        
        // Warning header
        val headerLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF5722"))
            setPadding(48, 40, 48, 32)
        }
        
        val warningIcon = TextView(context).apply {
            text = "⚠️ ID Already Exists"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(warningIcon)
        
        val warningText = TextView(context).apply {
            text = "Participant ID \"$userId\" has already been used."
            textSize = 14f
            setTextColor(Color.parseColor("#FFCCBC"))
            setPadding(0, 8, 0, 0)
        }
        headerLayout.addView(warningText)
        
        mainLayout.addView(headerLayout)
        
        // Content
        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        val infoText = TextView(context).apply {
            text = "Please choose a different ID or continue with this ID if you want to add more data for this participant."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(6f, 1f)
        }
        contentLayout.addView(infoText)
        
        // Show existing IDs
        val existingTitle = TextView(context).apply {
            text = "\nExisting Participant IDs:"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
        contentLayout.addView(existingTitle)
        
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                300
            )
        }
        
        val idsList = TextView(context).apply {
            text = existingIds.joinToString("\n") { "  •  $it" }
            textSize = 14f
            setTextColor(Color.parseColor("#1976D2"))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scrollView.addView(idsList)
        contentLayout.addView(scrollView)
        
        // Suggestion for next ID
        val nextId = suggestNextId(existingIds)
        val suggestionText = TextView(context).apply {
            text = "\n💡 Suggested next ID: $nextId"
            textSize = 14f
            setTextColor(Color.parseColor("#388E3C"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        contentLayout.addView(suggestionText)
        
        mainLayout.addView(contentLayout)
        
        AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setView(mainLayout)
            .setPositiveButton("Use Anyway") { _, _ ->
                initializeExperiment(userId)
            }
            .setNegativeButton("Change ID") { dialog, _ ->
                userIdInput.setText(nextId)
                userIdInput.setSelection(nextId.length)
                dialog.dismiss()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    private fun suggestNextId(existingIds: List<String>): String {
        // Try to find numeric IDs and suggest next number
        val numericIds = existingIds.mapNotNull { it.toIntOrNull() }.sorted()
        return if (numericIds.isNotEmpty()) {
            (numericIds.max() + 1).toString()
        } else {
            // If no numeric IDs, suggest "1" or append number
            "1"
        }
    }

    private fun initializeExperiment(userId: String) {
        
        try {
            val config = Aura.Config(
                context = applicationContext,
                experimentID = "Fitts_Law_Exp",
                userID = userId,
                couchDbUrl = "https://couchdb.hci.uni-hannover.de",
                dbName = "aura",
                username = BuildConfig.COUCHDB_USER,
                password = BuildConfig.COUCHDB_PASSWORD,
                availableConditions = conditions.map { it.name },
                counterbalanceConfig = Aura.CounterbalanceConfig(
                    mode = Aura.CounterbalanceMode.CUSTOM,
                    customOrders = mapOf(
                        "default" to listOf("Medium", "Small", "Large")
                    )
                )
            )
            
            Aura.setupExperiment(config)
            
            // Get counterbalanced order with full metadata
            val cbResult = Aura.getCounterbalancedOrder()
            conditionOrder = cbResult.conditionOrder
            
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
            val cbResult = try { Aura.getCounterbalancedOrder() } catch (e: Exception) { null }
            
            val modeName = when (cbResult?.mode) {
                Aura.CounterbalanceMode.LATIN_SQUARE -> "Latin Square"
                Aura.CounterbalanceMode.FULL_PERMUTATION -> "Full Permutation"
                Aura.CounterbalanceMode.RANDOM -> "Random"
                Aura.CounterbalanceMode.CUSTOM -> "Custom (Fixed Order)"
                Aura.CounterbalanceMode.LEGACY -> "Legacy"
                else -> "Unknown"
            }
            
            val orderInfo = cbResult?.let {
                "$modeName (${it.totalGroups} groups):\n" +
                it.allOrders.mapIndexed { idx, order -> 
                    "  Group $idx: ${order.joinToString(" → ")}"
                }.joinToString("\n")
            } ?: "N/A"
            
            "Study: Fitts' Law\n\n" +
            "Counterbalancing Mode: $modeName\n\n" +
            orderInfo + "\n\n" +
            "Your group: ${cbResult?.groupIndex ?: "?"}\n" +
            "Your order: ${conditionOrder.joinToString(" → ")}\n" +
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
            
            // Create professional participant selection dialog
            val sortedUsers = userIds.sorted()
            showParticipantSelectionDialog(sortedUsers)
                
        } catch (e: Exception) {
            Toast.makeText(this, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showParticipantSelectionDialog(userIds: List<String>) {
        val context = this
        
        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        
        // Header
        val headerLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1976D2"))
            setPadding(48, 40, 48, 32)
        }
        
        val headerTitle = TextView(context).apply {
            text = "Experiment Results"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(headerTitle)
        
        val headerSubtitle = TextView(context).apply {
            text = "${userIds.size} participants"
            textSize = 14f
            setTextColor(Color.parseColor("#BBDEFB"))
            setPadding(0, 8, 0, 0)
        }
        headerLayout.addView(headerSubtitle)
        
        mainLayout.addView(headerLayout)
        
        // Scrollable list
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        
        val listLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setView(mainLayout)
            .setNegativeButton("Cancel", null)
            .create()
        
        userIds.forEachIndexed { index, userId ->
            val itemLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(48, 24, 48, 24)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                
                setOnClickListener {
                    dialog.dismiss()
                    loadUserResults(userId)
                }
            }
            
            val userIcon = TextView(context).apply {
                text = "👤"
                textSize = 20f
                setPadding(0, 0, 24, 0)
            }
            itemLayout.addView(userIcon)
            
            val userLabel = TextView(context).apply {
                text = "Participant $userId"
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            itemLayout.addView(userLabel)
            
            val arrowIcon = TextView(context).apply {
                text = "›"
                textSize = 24f
                setTextColor(Color.parseColor("#999999"))
            }
            itemLayout.addView(arrowIcon)
            
            listLayout.addView(itemLayout)
            
            // Add divider (except for last item)
            if (index < userIds.size - 1) {
                val divider = android.view.View(context).apply {
                    setBackgroundColor(Color.parseColor("#EEEEEE"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { marginStart = 96 }
                }
                listLayout.addView(divider)
            }
        }
        
        scrollView.addView(listLayout)
        mainLayout.addView(scrollView)
        
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.6).toInt()
        )
        
        dialog.show()
    }

    private fun loadUserResults(userId: String) {
        Toast.makeText(this, "Loading data for user $userId...", Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                // First, get ALL events for this user to see what's available
                val allEventsResponse = com.example.aura.lib.Aura.executeRawQuery(
                    """
                    {
                        "selector": {
                            "experiment_id": "Fitts_Law_Exp",
                            "user_id": "$userId"
                        },
                        "fields": ["event_name", "timestamp", "condition"],
                        "limit": 1000
                    }
                    """.trimIndent()
                )
                
                // Count event types
                val eventCounts = mutableMapOf<String, Int>()
                "\"event_name\":\"([^\"]+)\"".toRegex().findAll(allEventsResponse ?: "").forEach {
                    val eventName = it.groupValues[1]
                    eventCounts[eventName] = (eventCounts[eventName] ?: 0) + 1
                }
                
                android.util.Log.d("AURA_DEBUG", "Event counts for user $userId: $eventCounts")
                
                // Query for trial events (both old and new format)
                val response = com.example.aura.lib.Aura.executeRawQuery(
                    """
                    {
                        "selector": {
                            "experiment_id": "Fitts_Law_Exp",
                            "user_id": "$userId",
                            "event_name": {
                                "${"$"}in": ["target_hit", "target_clicked"]
                            }
                        },
                        "limit": 100000
                    }
                    """.trimIndent()
                )
                
                android.util.Log.d("AURA_DEBUG", "Query response length: ${response?.length ?: 0}")
                
                runOnUiThread {
                    if (response != null && response.contains("\"docs\"")) {
                        displayUserResults(userId, response, eventCounts)
                    } else {
                        showNoDataDialog(userId, "No trial events found.\n\nEvents in DB: $eventCounts")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AURA_DEBUG", "Query error", e)
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    data class TrialLogEntry(
        val timestamp: Long,
        val condition: String,
        val trial: Int,
        val reactionTimeMs: Long,
        val distance: Double,
        val targetSize: Int,
        val indexOfDifficulty: Double = 0.0
    )

    private fun displayUserResults(userId: String, jsonResponse: String, eventCounts: Map<String, Int> = emptyMap()) {
        try {
            val trials = mutableListOf<TrialLogEntry>()
            
            android.util.Log.d("AURA_DEBUG", "Response preview: ${jsonResponse.take(500)}")
            
            // Mango query returns: {"docs":[{doc1},{doc2},...]}
            // Each doc is a complete document with _id, condition, event_name, payload, timestamp, user_id
            
            // Find the docs array
            val docsStart = jsonResponse.indexOf("\"docs\":[")
            if (docsStart == -1) {
                android.util.Log.e("AURA_DEBUG", "No docs array found")
                showResultsDialog(userId, emptyList(), eventCounts)
                return
            }
            
            // Extract just the array content
            val arrayStart = docsStart + 8 // after "docs":[
            var depth = 1
            var arrayEnd = arrayStart
            while (depth > 0 && arrayEnd < jsonResponse.length) {
                when (jsonResponse[arrayEnd]) {
                    '[' -> depth++
                    ']' -> depth--
                }
                arrayEnd++
            }
            
            val docsArray = jsonResponse.substring(arrayStart, arrayEnd - 1)
            android.util.Log.d("AURA_DEBUG", "Docs array length: ${docsArray.length}")
            
            // Now split into individual documents
            // Each document starts with { and we need to track depth
            var i = 0
            while (i < docsArray.length) {
                // Find start of next document
                while (i < docsArray.length && docsArray[i] != '{') i++
                if (i >= docsArray.length) break
                
                val docStart = i
                var docDepth = 0
                var docEnd = i
                
                // Find end of this document by tracking brace depth
                while (docEnd < docsArray.length) {
                    when (docsArray[docEnd]) {
                        '{' -> docDepth++
                        '}' -> {
                            docDepth--
                            if (docDepth == 0) {
                                docEnd++
                                break
                            }
                        }
                    }
                    docEnd++
                }
                
                val docContent = docsArray.substring(docStart, docEnd)
                i = docEnd
                
                // Parse this document
                val trial = parseTrialFromDoc(docContent)
                if (trial != null) {
                    trials.add(trial)
                    android.util.Log.d("AURA_DEBUG", "Parsed trial: ${trial.condition} #${trial.trial} = ${trial.reactionTimeMs}ms")
                }
            }
            
            android.util.Log.d("AURA_DEBUG", "Total parsed trials: ${trials.size}")
            
            // Sort by timestamp
            val sortedTrials = trials.sortedBy { it.timestamp }
            
            // Build custom dialog with professional layout
            showResultsDialog(userId, sortedTrials, eventCounts)
                
        } catch (e: Exception) {
            android.util.Log.e("AURA_DEBUG", "Parse error", e)
            Toast.makeText(this, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun parseTrialFromDoc(docContent: String): TrialLogEntry? {
        // Check event type
        val eventNameMatch = "\"event_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(docContent)
        val eventName = eventNameMatch?.groupValues?.get(1) ?: return null
        
        if (eventName != "target_hit" && eventName != "target_clicked") return null
        
        // Extract condition (first occurrence, which is the outer one)
        val conditionMatch = "\"condition\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(docContent)
        val condition = conditionMatch?.groupValues?.get(1) ?: return null
        
        // Extract timestamp
        val timestampMatch = "\"timestamp\"\\s*:\\s*(\\d+)".toRegex().find(docContent)
        val timestamp = timestampMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        
        // Find payload section - need to handle nested braces
        val payloadStart = docContent.indexOf("\"payload\"")
        if (payloadStart == -1) return null
        
        val braceStart = docContent.indexOf('{', payloadStart)
        if (braceStart == -1) return null
        
        var depth = 0
        var braceEnd = braceStart
        while (braceEnd < docContent.length) {
            when (docContent[braceEnd]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            braceEnd++
        }
        
        val payloadContent = docContent.substring(braceStart, braceEnd + 1)
        
        // Extract values from payload - handle integers, decimals, and .0 suffix
        val trialMatch = "\"trial(?:_number)?\"\\s*:\\s*([\\d.]+)".toRegex().find(payloadContent)
        val trial = trialMatch?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0
        
        val timeMatch = "\"(?:reaction_time_ms|time_ms)\"\\s*:\\s*([\\d.]+)".toRegex().find(payloadContent)
        val reactionTime = timeMatch?.groupValues?.get(1)?.toDoubleOrNull()?.toLong() ?: 0L
        
        val distanceMatch = "\"distance_(?:px|error_px)\"\\s*:\\s*([\\d.E-]+)".toRegex().find(payloadContent)
        val distance = distanceMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        
        val sizeMatch = "\"target_size_(?:dp|px)\"\\s*:\\s*([\\d.]+)".toRegex().find(payloadContent)
        val targetSize = sizeMatch?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0
        
        val idMatch = "\"index_of_difficulty\"\\s*:\\s*([\\d.E-]+)".toRegex().find(payloadContent)
        val indexOfDifficulty = idMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        
        if (condition.isEmpty() || reactionTime <= 0) return null
        
        return TrialLogEntry(timestamp, condition, trial, reactionTime, distance, targetSize, indexOfDifficulty)
    }
    
    private fun showResultsDialog(userId: String, trials: List<TrialLogEntry>, eventCounts: Map<String, Int>) {
        val context = this
        
        // Create main container
        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        
        // Header section (fixed, not scrollable)
        val headerLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(48, 32, 48, 24)
        }
        
        val headerTitle = TextView(context).apply {
            text = "Participant $userId"
            textSize = 20f
            setTextColor(Color.parseColor("#1976D2"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(headerTitle)
        
        val headerSubtitle = TextView(context).apply {
            text = if (trials.isNotEmpty()) {
                "${trials.size} trials • ${trials.groupBy { it.condition }.size} conditions"
            } else {
                "No trial data"
            }
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
        }
        headerLayout.addView(headerSubtitle)
        
        mainLayout.addView(headerLayout)
        
        // Divider
        val divider = android.view.View(context).apply {
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
            )
        }
        mainLayout.addView(divider)
        
        // Scrollable content
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        
        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 48)
        }
        
        if (trials.isEmpty()) {
            // No data message
            val noDataText = TextView(context).apply {
                text = "No trial data found for this participant.\n\n" +
                       "Events in database:\n" +
                       eventCounts.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
            }
            contentLayout.addView(noDataText)
        } else {
            // Group by condition in order of appearance
            val conditionsInOrder = trials.map { it.condition }.distinct()
            
            conditionsInOrder.forEach { condition ->
                val conditionTrials = trials.filter { it.condition == condition }
                
                // Condition header
                val condHeader = TextView(context).apply {
                    text = "▸ $condition"
                    textSize = 16f
                    setTextColor(Color.parseColor("#333333"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, 24, 0, 8)
                }
                contentLayout.addView(condHeader)
                
                // Condition stats bar
                val times = conditionTrials.map { it.reactionTimeMs }
                val statsBar = TextView(context).apply {
                    text = "Avg: ${times.average().toInt()}ms  •  Min: ${times.minOrNull()}ms  •  Max: ${times.maxOrNull()}ms"
                    textSize = 12f
                    setTextColor(Color.parseColor("#1976D2"))
                    setPadding(16, 0, 0, 12)
                }
                contentLayout.addView(statsBar)
                
                // Trial list with alternating background
                conditionTrials.forEachIndexed { index, trial ->
                    val trialRow = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        setPadding(16, 12, 16, 12)
                        if (index % 2 == 0) {
                            setBackgroundColor(Color.parseColor("#FAFAFA"))
                        }
                    }
                    
                    val trialNum = TextView(context).apply {
                        text = "#${trial.trial}"
                        textSize = 13f
                        setTextColor(Color.parseColor("#888888"))
                        layoutParams = android.widget.LinearLayout.LayoutParams(80, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    trialRow.addView(trialNum)
                    
                    val trialTime = TextView(context).apply {
                        text = "${trial.reactionTimeMs}ms"
                        textSize = 14f
                        setTextColor(Color.parseColor("#333333"))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    trialRow.addView(trialTime)
                    
                    if (trial.distance > 0) {
                        val trialDist = TextView(context).apply {
                            text = "${trial.distance.toInt()}px"
                            textSize = 12f
                            setTextColor(Color.parseColor("#888888"))
                        }
                        trialRow.addView(trialDist)
                    }
                    
                    contentLayout.addView(trialRow)
                }
            }
            
            // Overall summary section
            val summaryDivider = android.view.View(context).apply {
                setBackgroundColor(Color.parseColor("#1976D2"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 3
                ).apply { topMargin = 32 }
            }
            contentLayout.addView(summaryDivider)
            
            val summaryTitle = TextView(context).apply {
                text = "OVERALL SUMMARY"
                textSize = 14f
                setTextColor(Color.parseColor("#1976D2"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 16)
            }
            contentLayout.addView(summaryTitle)
            
            val allTimes = trials.map { it.reactionTimeMs }
            val summaryStats = TextView(context).apply {
                text = buildString {
                    append("Total Trials: ${allTimes.size}\n")
                    append("Average RT: ${allTimes.average().toInt()}ms\n")
                    append("Minimum RT: ${allTimes.minOrNull()}ms\n")
                    append("Maximum RT: ${allTimes.maxOrNull()}ms")
                }
                textSize = 14f
                setTextColor(Color.parseColor("#333333"))
                setLineSpacing(8f, 1f)
            }
            contentLayout.addView(summaryStats)
        }
        
        scrollView.addView(contentLayout)
        mainLayout.addView(scrollView)
        
        // Create dialog
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setView(mainLayout)
            .setPositiveButton("Close", null)
            .create()
        
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
        
        dialog.show()
    }
    
    private fun showNoDataDialog(userId: String, reason: String) {
        AlertDialog.Builder(this)
            .setTitle("Results: User $userId")
            .setMessage("No trial data found\n\nReason: $reason")
            .setPositiveButton("OK", null)
            .show()
    }
}
