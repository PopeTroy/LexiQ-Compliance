package org.linguistic.assistant

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.wifi.WifiManager
import android.view.inputmethod.InputConnection
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class LexiQKeyboardService : InputMethodService() {

    private val onnxEngine = OnnxSecurityEngine()
    private var wifiManager: WifiManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    // Whitelisted Home and Work BSSIDs/MACs (Client Specific)
    private val trustedEnvironments = setOf(
        "11:22:33:44:55:66", // Home Router BSSID
        "AA:BB:CC:DD:EE:FF"  // Work Access Point BSSID
    )

    // Per-Client Secret Security Code
    private val CLIENT_SECURITY_CODE = "CLIENT-7890-SECURE"

    private var isDeviceLocked = false
    private var unknownWifiCount = 0
    private var unknownBleCount = 0
    private var isTrustedNetworkVisible = false

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Initialize local INT8 Quantized ONNX model compiled via Xeno-IR
        val modelPath = "${applicationContext.filesDir.absolutePath}/security_quant_int8.onnx"
        onnxEngine.initQuantizedModel(modelPath)

        registerRfReceivers()
    }

    private fun registerRfReceivers() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        registerReceiver(rfScanReceiver, filter)
    }

    private val rfScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> evaluateEnvironmentState()
                BluetoothDevice.ACTION_FOUND -> evaluateBluetoothNode(intent)
            }
        }
    }

    private fun evaluateBluetoothNode(intent: Intent) {
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val mac = device?.address
        if (mac != null && !trustedEnvironments.contains(mac)) {
            unknownBleCount++
        }
    }

    private fun evaluateEnvironmentState() {
        val scanResults = wifiManager?.scanResults ?: return
        unknownWifiCount = 0
        isTrustedNetworkVisible = false

        for (result in scanResults) {
            val bssid = result.BSSID
            if (trustedEnvironments.contains(bssid)) {
                isTrustedNetworkVisible = true
            } else {
                unknownWifiCount++
            }
        }

        // Construct telemetry vector for ONNX Inference
        val features = floatArrayOf(
            unknownWifiCount.toFloat(),
            unknownBleCount.toFloat(),
            scanResults.size.toFloat(),
            if (isTrustedNetworkVisible) 1.0f else 0.0f
        )

        val threatScore = onnxEngine.classifyEnvironmentThreat(features)

        // Local ONNX decision boundary
        if (isTrustedNetworkVisible) {
            executeAutoUnbrickSequence()
        } else if (threatScore > 0.75f && !isDeviceLocked) {
            triggerNimClientVerification("Elevated RF Threat Level ($threatScore)")
        }
    }

    private fun executeAutoUnbrickSequence() {
        if (isDeviceLocked) {
            isDeviceLocked = false
            handler.post {
                currentBtnBg = "#00E676" // Neon Green
                currentTxtColor = "#000000"
                tikiIcon?.text = "🗿"
                suggestionHeader?.text = "LexiQ Engine: Trusted Zone Detected — System Active ✅"
                suggestionHeader?.setTextColor(Color.parseColor("#00E676"))
                renderKeyboardLayout()
            }
        }
    }

    private fun triggerNimClientVerification(reason: String) {
        isDeviceLocked = true
        handler.post {
            currentBtnBg = "#FF1744" // Neon Red
            currentTxtColor = "#FFFFFF"
            tikiIcon?.text = "🔒"
            suggestionHeader?.text = "DEVICE LOCKED: Input Client User Code to Proceed"
            suggestionHeader?.setTextColor(Color.parseColor("#FF1744"))
            renderKeyboardLayout()
        }

        // Route environment assessment log to NVIDIA NIM Microservice
        dispatchTelemetryToNvidiaNim(reason)
    }

    /**
     * Intercept keystrokes when locked to validate against the Client User Code
     */
    private fun handleKeyPress(key: String) {
        val ic: InputConnection = currentInputConnection ?: return

        if (isDeviceLocked) {
            when (key) {
                "ENTER" -> {
                    val inputCode = wordBuffer.toString().trim()
                    if (inputCode == CLIENT_SECURITY_CODE) {
                        isDeviceLocked = false
                        wordBuffer.clear()
                        executeAutoUnbrickSequence()
                    } else {
                        wordBuffer.clear()
                        suggestionHeader?.text = "❌ INVALID CLIENT CODE — ACCESS DENIED"
                    }
                }
                "DEL" -> {
                    if (wordBuffer.isNotEmpty()) wordBuffer.deleteCharAt(wordBuffer.length - 1)
                }
                else -> {
                    wordBuffer.append(key)
                }
            }
            return
        }

        // Standard Keyboard execution if unlocked...
        ic.commitText(key, 1)
    }

    private fun dispatchTelemetryToNvidiaNim(reason: String) {
        thread {
            try {
                val url = URL("https://integrate.api.nvidia.com/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer YOUR_NVIDIA_NIM_KEY")
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("model", "meta/llama-3.1-70b-instruct")
                    put("messages", arrayOf(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", "You are LexiQ Endpoint Security Coordinator. Log lock enforcement events.")
                        },
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "Enforcement Reason: $reason. Unknown Wi-Fis: $unknownWifiCount, Unknown BLE: $unknownBleCount.")
                        }
                    ))
                }

                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val responseCode = conn.responseCode
                // Logging and cloud analytics handled asynchronously...
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
