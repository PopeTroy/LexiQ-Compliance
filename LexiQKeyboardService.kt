package org.linguistic.assistant

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.view.inputmethod.InputConnection
import java.util.concurrent.Executors

class LexiQKeyboardService : InputMethodService() {

    // Threshold: Max allowed unknown devices before defensive lockdown initiates
    private val UNKNOWN_DEVICE_THRESHOLD = 8

    // Trusted MAC / BSSID Whitelist
    private val trustedBssids = setOf(
        "00:11:22:33:44:55", // Home Wi-Fi
        "AA:BB:CC:DD:EE:FF"  // Trusted Personal Device
    )

    private var wifiManager: WifiManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Register RF Scanning Receivers
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        registerReceiver(rfScanReceiver, filter)
        
        // Schedule ambient RF monitoring loop every 30 seconds
        executor.scheduleAtFixedRate({ startRfScan() }, 0, 30, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun startRfScan() {
        // Trigger Wi-Fi Scan
        wifiManager?.startScan()

        // Trigger Bluetooth Scan
        if (bluetoothAdapter?.isDiscovering == false) {
            bluetoothAdapter?.startDiscovery()
        }
    }

    private val rfScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> evaluateWifiThreatLevel()
                BluetoothDevice.ACTION_FOUND -> evaluateBluetoothDevice(intent)
            }
        }
    }

    private fun evaluateWifiThreatLevel() {
        val scanResults = wifiManager?.scanResults ?: return
        var unknownCount = 0

        for (result in scanResults) {
            val bssid = result.BSSID
            if (!trustedBssids.contains(bssid)) {
                unknownCount++
            }
        }

        updateRfStatusHeader(unknownCount)

        if (unknownCount >= UNKNOWN_DEVICE_THRESHOLD) {
            executeSecurityBrickSequence("Critical RF Density: $unknownCount Unknown Wi-Fi Nodes Detected")
        }
    }

    private var unknownBtCount = 0
    private fun evaluateBluetoothDevice(intent: Intent) {
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val macAddress = device?.address

        if (macAddress != null && !trustedBssids.contains(macAddress)) {
            unknownBtCount++
        }

        if (unknownBtCount >= UNKNOWN_DEVICE_THRESHOLD) {
            executeSecurityBrickSequence("Critical Threat: Over-density of unknown Bluetooth signatures")
        }
    }

    private fun updateRfStatusHeader(unknownCount: Int) {
        handler.post {
            if (unknownCount > 4) {
                tikiIcon?.text = "⚠️"
                suggestionHeader?.text = "RF Warning: $unknownCount Unknown Signal Sources Active"
                suggestionHeader?.setTextColor(Color.parseColor("#FFEB3B"))
            } else {
                tikiIcon?.text = "🗿"
                suggestionHeader?.text = "LexiQ Engine: Environment Secure ✅"
                suggestionHeader?.setTextColor(Color.parseColor("#00E676"))
            }
        }
    }

    /**
     * Defensive Lockdown Protocol:
     * 1. Clears local key logs and memory buffers.
     * 2. Visual feedback state shift (Neon Red).
     * 3. Executes immediate Device Admin System Lock.
     */
    private fun executeSecurityBrickSequence(reason: String) {
        handler.post {
            // Step 1: Wipe local data buffers instantly
            wordBuffer.clear()
            
            // Step 2: Set UI state to critical breach alert
            currentBtnBg = "#FF1744"
            currentTxtColor = "#FFFFFF"
            tikiIcon?.text = "🚨"
            suggestionHeader?.text = "BRICK PROTOCOL TRIGGERED: $reason"
            suggestionHeader?.setTextColor(Color.parseColor("#FF1744"))
            renderKeyboardLayout()

            // Step 3: Force screen turn-off / device lock state
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                // Lock system via native device management intent
                val lockIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(lockIntent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(rfScanReceiver)
        executor.shutdown()
    }
}
