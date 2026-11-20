package org.linguistic.assistant

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class OnnxSecurityEngine {

    external fun initQuantizedModel(modelPath: String): Boolean

    /**
     * Evaluates environment telemetry array:
     * [unknownWifiCount, unknownBleCount, totalWifiCount, isHomeBssidPresent, isWorkBssidPresent]
     * Returns threat classification score (0.0 = Secure Home/Work, 1.0 = Untrusted Perimeter)
     */
    external fun classifyEnvironmentThreat(features: FloatArray): Float

    data class CalibrationSummary(
        val sampleCount: Int,
        val minVal: Float,
        val maxVal: Float,
        val int8Scale: Float,
        val estimatedDistortionBits: Float
    )

    private val calibrationSamples = mutableListOf<FloatArray>()
    private var loadedModelPath: String? = null
    private var isModelInitialized: Boolean = false

    fun initializeSafe(modelPath: String): Boolean {
        val file = File(modelPath)
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            isModelInitialized = false
            return false
        }
        return try {
            val ok = initQuantizedModel(file.absolutePath)
            isModelInitialized = ok
            if (ok) {
                loadedModelPath = file.absolutePath
            }
            ok
        } catch (_: Throwable) {
            isModelInitialized = false
            false
        }
    }

    fun releaseModel() {
        loadedModelPath = null
        isModelInitialized = false
        calibrationSamples.clear()
    }

    /**
     * Collects feature batches for offline PTQ/QAT calibration & drift auditing.
     */
    fun recordCalibrationBatch(features: FloatArray) {
        if (features.isEmpty()) return
        calibrationSamples.add(features.copyOf())
        if (calibrationSamples.size > 512) {
            calibrationSamples.removeAt(0)
        }
    }

    fun computeCalibrationSummary(scalingK: Float = 0.666f): CalibrationSummary {
        if (calibrationSamples.isEmpty()) {
            return CalibrationSummary(0, 0f, 0f, 1f / 127f, 0f)
        }
        var minV = Float.POSITIVE_INFINITY
        var maxV = Float.NEGATIVE_INFINITY
        for (batch in calibrationSamples) {
            for (v in batch) {
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
        }
        val absMax = maxOf(abs(minV), abs(maxV), 1e-6f)
        val int8Scale = absMax / 127f
        val fp32Eps = 1.1920929e-7f
        val ratio = (int8Scale / fp32Eps).coerceAtLeast(1f)
        val distortionBits = scalingK * (ln(ratio.toDouble()) / ln(2.0)).toFloat() * 0.01f
        return CalibrationSummary(
            sampleCount = calibrationSamples.size,
            minVal = minV,
            maxVal = maxV,
            int8Scale = int8Scale,
            estimatedDistortionBits = distortionBits
        )
    }

    /**
     * Converts FP32 features to symmetric INT8 ByteBuffer for quantized model ingestion.
     */
    fun quantizeFeaturesToInt8(features: FloatArray, scale: Float): ByteBuffer {
        val safeScale = if (scale > 1e-9f) scale else 1f / 127f
        val buffer = ByteBuffer.allocateDirect(features.size).order(ByteOrder.nativeOrder())
        for (value in features) {
            val q = (value / safeScale).toInt().coerceIn(-128, 127).toByte()
            buffer.put(q)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Evaluates threat safely using native JNI when initialized, with a deterministic
     * local heuristic fallback if native library/model is not loaded.
     */
    fun evaluateThreatSafe(features: FloatArray): Float {
        recordCalibrationBatch(features)
        if (isModelInitialized) {
            try {
                return classifyEnvironmentThreat(features).coerceIn(0f, 1f)
            } catch (_: Throwable) {
                // Fall through to deterministic local evaluation
            }
        }
        val unknownWifi = features.getOrElse(0) { 0f }
        val unknownBle = features.getOrElse(1) { 0f }
        val totalWifi = features.getOrElse(2) { 1f }.coerceAtLeast(1f)
        val homePresent = features.getOrElse(3) { 0f } > 0.5f
        val workPresent = features.getOrElse(4) { 0f } > 0.5f

        if (homePresent || workPresent) {
            return ((unknownWifi + unknownBle * 0.5f) / (totalWifi + 4f)).coerceIn(0f, 0.25f)
        }
        val perimeterPressure = (unknownWifi * 0.15f) + (unknownBle * 0.1f) + (0.35f / totalWifi)
        return perimeterPressure.coerceIn(0f, 1f)
    }

    fun embeddingCosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        if (len == 0) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 1e-12) return 0f
        return (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    companion object {
        init {
            try {
                System.loadLibrary("lexiq_onnx_security")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
