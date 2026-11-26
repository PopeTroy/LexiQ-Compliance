package org.linguistic.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "org.linguistic.assistant/onnx_engine"
    private val telemetryChannelName = "org.linguistic.assistant/telemetry_service"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val engine = OnnxEngine.getOrCreate(this)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "initModel" -> {
                    val modelPath = call.argument<String>("modelPath")
                    if (modelPath.isNullOrBlank()) {
                        result.error("INVALID_ARG", "modelPath is required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        val ok = engine.initModel(modelPath)
                        result.success(ok)
                    } catch (e: Throwable) {
                        result.error("INIT_FAILED", e.message, null)
                    }
                }

                "destroyModel" -> {
                    try {
                        engine.destroyModel()
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
                        val out: FloatArray? = engine.evaluateBehavior(floats)
                        result.success(out?.map { value -> value.toDouble() })
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
                        val out: ByteArray? = engine.evaluateBehaviorInt8(bytes)
                        result.success(out)
                    } catch (e: Throwable) {
                        result.error("EVAL_INT8_FAILED", e.message, null)
                    }
                }

                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, telemetryChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startSensorTelemetry" -> {
                        val intent = Intent(this, SensorNotificationTelemetryService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        result.success(null)
                    }

                    "stopSensorTelemetry" -> {
                        val intent = Intent(this, SensorNotificationTelemetryService::class.java)
                        stopService(intent)
                        result.success(null)
                    }

                    "sendCommand" -> {
                        val cmd = call.argument<String>("command")?.trim().orEmpty()
                        val intent = Intent(this, SensorNotificationTelemetryService::class.java).apply {
                            action = SensorNotificationTelemetryService.ACTION_COMMAND
                            putExtra(SensorNotificationTelemetryService.EXTRA_COMMAND_TEXT, cmd)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        result.success("Sent to notification service: $cmd")
                    }

                    "setNimApiKey" -> {
                        val apiKey = call.argument<String>("apiKey")?.trim().orEmpty()
                        if (apiKey.isNotEmpty()) {
                            getSharedPreferences("lexiq_nim_config", Context.MODE_PRIVATE)
                                .edit()
                                .putString("nvidia_api_key", apiKey)
                                .apply()
                        }
                        result.success(apiKey.isNotEmpty())
                    }

                    else -> result.notImplemented()
                }
            }
    }
}
