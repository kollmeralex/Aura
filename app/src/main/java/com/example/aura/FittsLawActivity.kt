package com.example.aura

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aura.lib.Aura
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class FittsLawActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var instructionText: TextView
    private lateinit var actionButton: Button

    private var conditionOrder: List<String> = emptyList()
    private var currentConditionIndex = 0
    private var trialsCompleted = 0
    private val MAX_TRIALS = 10 // 10 targets per condition

    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout Setup
        container = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        
        instructionText = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            text = "Loading..."
        }
        val paramsText = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, 
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = android.view.Gravity.CENTER }
        
        actionButton = Button(this).apply {
            text = "Start"
            textSize = 20f
            setOnClickListener { startConditionRun() }
        }
        val paramsBtn = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, 
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { 
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = 100
        }

        container.addView(instructionText, paramsText)
        container.addView(actionButton, paramsBtn)
        setContentView(container)

        // Get Order
        conditionOrder = Aura.getSuggestedConditionOrder()
        if (conditionOrder.isEmpty()) {
            instructionText.text = "Error: No conditions found. Initialize Aura first."
            actionButton.visibility = View.GONE
        } else {
            showIntroForCondition(currentConditionIndex)
        }
    }

    private fun showIntroForCondition(index: Int) {
        if (index >= conditionOrder.size) {
            instructionText.text = "Experiment Complete!\nThank you."
            actionButton.text = "Close"
            actionButton.setOnClickListener { finish() }
            actionButton.visibility = View.VISIBLE
            return
        }

        val condition = conditionOrder[index]
        instructionText.text = "Condition ${index + 1} / ${conditionOrder.size}\n\nPlease use your:\n\n$condition"
        actionButton.text = "Start Task"
        actionButton.visibility = View.VISIBLE
        actionButton.setOnClickListener { 
            Aura.setCondition(condition)
            startConditionRun() 
        }
    }

    private fun startConditionRun() {
        instructionText.visibility = View.GONE
        actionButton.visibility = View.GONE
        trialsCompleted = 0
        showNextTarget()
    }

    private fun showNextTarget() {
        if (trialsCompleted >= MAX_TRIALS) {
            currentConditionIndex++
            instructionText.visibility = View.VISIBLE
            showIntroForCondition(currentConditionIndex)
            return
        }

        // Create Target (Red Circle)
        val targetSize = 150 // px
        val target = View(this).apply {
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_background) // Fallback generic shape
            // Better: simple circle drawable logic or color
            setBackgroundColor(Color.RED) 
        }

        // FIX: Get safe area insets to avoid status bar/nav bar
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(container)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        
        val safeTop = insets?.top ?: 0
        val safeBottom = insets?.bottom ?: 0
        val safeLeft = insets?.left ?: 0
        val safeRight = insets?.right ?: 0

        // Random Position (within safe bounds)
        val maxX = container.width - targetSize - safeRight
        val maxY = container.height - targetSize - safeBottom
        
        if (maxX <= safeLeft || maxY <= safeTop) {
            // Wait for layout if dimensions are not ready or too small
            Handler(Looper.getMainLooper()).postDelayed({ showNextTarget() }, 100)
            return
        }
        
        val randomX = Random.nextInt(safeLeft, maxX).toFloat()
        val randomY = Random.nextInt(safeTop, maxY).toFloat()

        val params = FrameLayout.LayoutParams(targetSize, targetSize).apply {
            leftMargin = randomX.toInt()
            topMargin = randomY.toInt()
        }
        
        target.layoutParams = params
        target.setOnClickListener { 
            // Click handled via TouchListener for precision, but this triggers logic
        }

        target.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val touchX = event.rawX
                val touchY = event.rawY
                
                // Calculate center of target
                val targetCenterX = randomX + targetSize / 2
                val targetCenterY = randomY + targetSize / 2
                
                // Euclidean distance
                val distance = sqrt((touchX - targetCenterX).pow(2) + (touchY - targetCenterY).pow(2))
                
                val timeTaken = System.currentTimeMillis() - startTime

                // Log to Aura
                Aura.logEvent("target_clicked", mapOf(
                    "trial_number" to trialsCompleted + 1,
                    "target_x" to targetCenterX,
                    "target_y" to targetCenterY,
                    "touch_x" to touchX,
                    "touch_y" to touchY,
                    "distance_error_px" to distance,
                    "time_ms" to timeTaken,
                    "target_size_px" to targetSize
                ))

                container.removeView(v)
                trialsCompleted++
                showNextTarget()
                true
            } else {
                false
            }
        }

        container.addView(target)
        startTime = System.currentTimeMillis()
    }
}