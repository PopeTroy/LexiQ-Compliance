package org.linguistic.assistant

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "org.linguistic.assistant/onnx_engine"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "initModel" -> {
                    val modelPath = call.argument<String>("modelPath")
                    if (modelPath.isNullOrBlank()) {
                        result.error("INVALID_ARG", "modelPath is required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        val ok = OnnxEngine.initModel(modelPath)
                        result.success(ok)
                    } catch (e: Throwable) {
                        result.error("INIT_FAILED", e.message, null)
                    }
                }

                "destroyModel" -> {
                    try {
                        OnnxEngine.destroyModel()
                        result.success(null)
                    } catch (e: Throwable) {
                        result.error("DESTROY_FAILED", e.message, null)
                    }
                }

                "evaluateBehavior" -> {
                    val rawList = call.argument<List<*>>("features")
                    if (rawList == null || rawList.isEmpty()) {
                        result.success(null)
                        return@setMethodCallHandler
                    }
                    try {
                        val floats = FloatArray(rawList.size) { idx ->
                            val item = rawList[idx] as? Number
                                ?: throw IllegalArgumentException("Non-numeric feature at index $idx")
                            item.toFloat()
                        }
                        val out = OnnxEngine.evaluateBehavior(floats)
                        result.success(out?.map { it.toDouble() })
                    } catch (e: Throwable) {
                        result.error("EVAL_FLOAT_FAILED", e.message, null)
                    }
                }

                "evaluateBehaviorInt8" -> {
                    val bytes = call.argument<ByteArray>("quantizedFeatures")
                    if (bytes == null || bytes.isEmpty()) {
                        result.success(null)
                        return@setMethodCallHandler
                    }
                    try {
                        val out = OnnxEngine.evaluateBehaviorInt8(bytes)
                        result.success(out)
                    } catch (e: Throwable) {
                        result.error("EVAL_INT8_FAILED", e.message, null)
                    }
                }

                else -> result.notImplemented()
            }
        }
    }
}
