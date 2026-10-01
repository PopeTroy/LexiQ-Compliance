package org.linguistic.assistant

import android.content.Context
import android.graphics.PointF
import android.content.Intent
import android.provider.Settings
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class OnnxEngine(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Behavioral & INT8 Storage Keys
    private val prefName = "lexiq_int8_user_profile"
    private var userDwellBias: Float = 0.0f
    private var userPressureScale: Float = 1.0f
    private var tapCount: Int = 0

    // Behavioral Trait Metrics
    private var typingSpeedWpm: Float = 40.0f
    private var aggressionIndex: Float = 0.0f
    private var trollBehaviorScore: Float = 0.0f
    private var activePackageName: String = "unknown.app"

    // Scheduled Post Data Structure
    data class ScheduledPost(
        val targetApp: String,
        val text: String,
        val timestampMs: Long
    )
    private val scheduledQueue = mutableListOf<ScheduledPost>()

    // Native JNI Methods
    external fun initModel(modelPath: String)
    external fun evaluateBehavior(features: FloatArray): FloatArray

    companion object {
        init {
            try {
                System.loadLibrary("lexiq_onnx_native")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    init {
        loadModelFromAssets()
        loadInt8BehavioralProfile()
    }

    private fun loadModelFromAssets() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = File(context.filesDir, "onnx.cql")
            if (!modelFile.exists()) {
                context.assets.open("onnx.cql").use { inputStream ->
                    FileOutputStream(outputStream ->
                        inputStream.copyTo(outputStream)
                    )
                }
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            initModel(modelFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Physics & Behavioral Dynamic Correction Engine
     * Measures force, dwell, speed, and aggression to stabilize touch input
     */
    fun calculatePhysicsAwareTarget(
        rawX: Float,
        rawY: Float,
        pressure: Float,
        touchMajor: Float,
        dwellTimeMs: Long,
        timeSinceLastTapMs: Long
    ): PointF {
        // Physics Model: Velocity & Force Kinetics
        val estimatedForce = pressure * touchMajor
        val velocityFactor = if (dwellTimeMs > 0) (100.0f / dwellTimeMs.toFloat()) else 1.0f

        // Aggression & Speed Analytics
        val rawWpm = if (timeSinceLastTapMs > 0) (60000.0f / timeSinceLastTapMs) / 5.0f else 40.0f
        typingSpeedWpm = (typingSpeedWpm * 0.9f) + (rawWpm * 0.1f)
        
        // High pressure + rapid hard taps indicate heightened kinetic friction / aggression
        val tapAggression = (estimatedForce * 0.6f) + (if (dwellTimeMs < 50) 0.4f else 0.0f)
        aggressionIndex = (aggressionIndex * 0.85f) + (tapAggression * 0.15f)

        // Kinetic Spatial Offsets
        val deltaX = (estimatedForce * 0.15f) * velocityFactor * userPressureScale
        val deltaY = (dwellTimeMs * 0.02f) + userDwellBias

        val calibratedX = rawX - deltaX
        val calibratedY = rawY - deltaY

        updateInt8BehavioralProfile(pressure, dwellTimeMs)

        return PointF(calibratedX, calibratedY)
    }

    /**
     * Real-time Text Interception, Anti-Troll Filtering, and Professional Transformation
     */
    fun processAndFilterText(
        inputText: String,
        targetApp: String
    ): String {
        this.activePackageName = targetApp

        // 1. Measure Troll and Hostility Indicators
        val trollKeywords = listOf("ratio", "cry about it", "stay mad", "noob", "stfu", "trash", "clown", "lmao loser")
        var toxicMatches = 0
        val lowerText = inputText.lowercase()

        for (word in trollKeywords) {
            if (lowerText.contains(word)) toxicMatches++
        }

        // Calculate combined Troll Score using key traits
        val capsRatio = if (inputText.isNotEmpty()) inputText.count { it.isUpperCase() }.toFloat() / inputText.length else 0.0f
        val exclamations = inputText.count { it == '!' || it == '?' }
        
        trollBehaviorScore = (toxicMatches * 0.4f) + (capsRatio * 0.3f) + (min(exclamations, 5) * 0.1f) + (aggressionIndex * 0.2f)

        // 2. Auto-Transform Text if Troll Score or Aggression Exceeds Threshold
        return if (trollBehaviorScore > 0.45f) {
            sanitizeToProfessionalTone(inputText)
        } else {
            optimizeGrammarAndTone(inputText)
        }
    }

    private fun sanitizeToProfessionalTone(rawText: String): String {
        var cleanText = rawText

        // Neutralize common inflammatory patterns
        cleanText = cleanText.replace("(?i)\\bstfu\\b".toRegex(), "please respect this position")
        cleanText = cleanText.replace("(?i)\\bnoob\\b".toRegex(), "uninformed participant")
        cleanText = cleanText.replace("(?i)\\btrash\\b".toRegex(), "suboptimal")
        cleanText = cleanText.replace("(?i)\\bratio\\b".toRegex(), "re-evaluate the context")
        cleanText = cleanText.replace("!+".toRegex(), ".")
        cleanText = cleanText.replace("\\?+".toRegex(), "?")

        // Format to unique, clear-headed professional phrasing
        if (cleanText.isNotBlank()) {
            cleanText = cleanText.lowercase().replaceFirstChar { it.uppercase() }
            if (!cleanText.endsWith(".") && !cleanText.endsWith("?")) {
                cleanText += "."
            }
        }
        return "I would suggest taking a more constructive approach: $cleanText"
    }

    private fun optimizeGrammarAndTone(inputText: String): String {
        if (inputText.isBlank()) return inputText
        var optimized = inputText.trim()
        
        // Ensure proper sentence capitalization and punctuation
        optimized = optimized.replaceFirstChar { it.uppercase() }
        if (!optimized.endsWith(".") && !optimized.endsWith("?") && !optimized.endsWith("!")) {
            optimized += "."
        }
        return optimized
    }

    /**
     * Cross-App Automation & Post Scheduling Engine
     */
    fun schedulePost(targetAppPackage: String, textToPost: String, delayMinutes: Long) {
        val executeAt = System.currentTimeMillis() + (delayMinutes * 60 * 1000)
        val sanitizedText = processAndFilterText(textToPost, targetAppPackage)
        
        scheduledQueue.add(ScheduledPost(targetAppPackage, sanitizedText, executeAt))
    }

    fun executeScheduledPosts() {
        val currentTime = System.currentTimeMillis()
        val iterator = scheduledQueue.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()
            if (currentTime >= item.timestampMs) {
                dispatchToTargetApp(item.targetApp, item.text)
                iterator.remove()
            }
        }
    }

    private fun dispatchToTargetApp(packageName: String, text: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_TEXT, text)
            intent.type = "text/plain"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * ONNX Local Quantized Inference
     */
    fun runInference(inputData: FloatArray, shape: LongArray): FloatArray? {
        if (ortEnv == null || ortSession == null) return null
        return try {
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputData), shape)
            val results = ortSession?.run(mapOf("input" to tensor))
            val outputTensor = results?.get(0) as? OnnxTensor
            outputTensor?.floatBuffer?.array()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadInt8BehavioralProfile() {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        userDwellBias = prefs.getFloat("dwell_bias_int8", 0.0f)
        userPressureScale = prefs.getFloat("pressure_scale_int8", 1.0f)
        tapCount = prefs.getInt("tap_count", 0)
        typingSpeedWpm = prefs.getFloat("wpm_int8", 40.0f)
        aggressionIndex = prefs.getFloat("aggression_int8", 0.0f)
    }

    private fun updateInt8BehavioralProfile(pressure: Float, dwellTimeMs: Long) {
        tapCount++
        userDwellBias = (userDwellBias * 0.95f) + ((dwellTimeMs - 120.0f) * 0.001f * 0.05f)
        userPressureScale = (userPressureScale * 0.98f) + (pressure * 0.02f)

        if (tapCount % 10 == 0) {
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().apply {
                putFloat("dwell_bias_int8", userDwellBias)
                putFloat("pressure_scale_int8", userPressureScale)
                putFloat("wpm_int8", typingSpeedWpm)
                putFloat("aggression_int8", aggressionIndex)
                putInt("tap_count", tapCount)
                apply()
            }
        }
    }
}
