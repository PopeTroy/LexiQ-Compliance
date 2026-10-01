private val onnxEngine = OnnxEngine()
private var lastKeyPressTime = System.currentTimeMillis()
private var backspaceCount = 0
private var totalKeystrokes = 0

private fun captureBehavioralFeatures(): FloatArray {
    val currentTime = System.currentTimeMillis()
    val typingInterval = (currentTime - lastKeyPressTime).toFloat()
    lastKeyPressTime = currentTime

    val errorRate = if (totalKeystrokes > 0) backspaceCount.toFloat() / totalKeystrokes else 0.0f
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY).toFloat()

    // 4-Dimensional Feature Vector: [Interval, ErrorRate, TotalLength, HourOfDay]
    return floatArrayOf(typingInterval, errorRate, totalKeystrokes.toFloat(), currentHour)
}

private fun processEnvironmentTelemetry() {
    val features = captureBehavioralFeatures()
    val behaviorEmbedding = onnxEngine.evaluateBehavior(features)

    // If local ONNX detects high cognitive load or intent state, delegate to NIM
    if (behaviorEmbedding.isNotEmpty() && behaviorEmbedding[0] > 0.8f) {
        dispatchToNvidiaNim(wordBuffer.toString())
    }
}
