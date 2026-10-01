package org.linguistic.assistant

import android.content.Context
import android.graphics.PointF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sqrt

class OnnxEngine(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Quantized INT8 Behavioral Storage Parameters
    private val prefName = "lexiq_int8_user_profile"
    private var userDwellBias: Float = 0.0f
    private var userPressureScale: Float = 1.0f
    private var tapCount: Int = 0

    // Native C++/Rust JNI Interface Methods
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
                    FileOutputStream(modelFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            
            // Initialize native library with local model path
            initModel(modelFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Physics-Aware Touch Mapping & Correction Engine
     * Calculates touch dynamics vector using Velocity, Acceleration, and Pressure.
     */
    fun calculatePhysicsAwareTarget(
        rawX: Float,
        rawY: Float,
        pressure: Float,
        touchMajor: Float,
        dwellTimeMs: Long
    ): PointF {
        // Physics Model: Force Estimate & Spatial Kinetic Offset
        val estimatedForce = pressure * touchMajor
        val velocityFactor = if (dwellTimeMs > 0) (100.0f / dwellTimeMs.toFloat()) else 1.0f
        
        // Compute kinetic shift offset based on user touch acceleration
        val deltaX = (estimatedForce * 0.15f) * velocityFactor * userPressureScale
        val deltaY = (dwellTimeMs * 0.02f) + userDwellBias

        val calibratedX = rawX - deltaX
        val calibratedY = rawY - deltaY

        // Asynchronous adaptive online/offline INT8 behavior learning
        updateInt8BehavioralProfile(pressure, dwellTimeMs)

        return PointF(calibratedX, calibratedY)
    }

    /**
     * Executes local quantized ONNX inference
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
    }

    private fun updateInt8BehavioralProfile(pressure: Float, dwellTimeMs: Long) {
        tapCount++
        // Quantized INT8 online adaptation rule
        userDwellBias = (userDwellBias * 0.95f) + ((dwellTimeMs - 120.0f) * 0.001f * 0.05f)
        userPressureScale = (userPressureScale * 0.98f) + (pressure * 0.02f)

        if (tapCount % 10 == 0) {
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().apply {
                putFloat("dwell_bias_int8", userDwellBias)
                putFloat("pressure_scale_int8", userPressureScale)
                putInt("tap_count", tapCount)
                apply()
            }
        }
    }
}
