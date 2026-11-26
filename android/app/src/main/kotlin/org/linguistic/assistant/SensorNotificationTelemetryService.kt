package org.linguistic.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlin.math.sqrt

class SensorNotificationTelemetryService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "lexiq_sensor_telemetry_channel"
        const val NOTIFICATION_ID = 40666
        const val EXTRA_COMMAND_TEXT = "extra_command_text"
        const val KEY_QUICK_PROMPT = "key_quick_prompt"
        const val ACTION_REFRESH = "org.linguistic.assistant.ACTION_REFRESH_TELEMETRY"
        const val ACTION_COMMAND = "org.linguistic.assistant.ACTION_SEND_COMMAND"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var engine: OnnxEngine
    private val pid = PIDComplianceController()
    private val handler = Handler(Looper.getMainLooper())

    private var accelMag: Float = 9.81f
    private var gyroNorm: Float = 0.0f
    private var magNorm: Float = 0.0f
    private var luxLevel: Float = 0.0f
    private var lastCommandResult: String = "Sensors online -> Notification pane active"

    private val updateRunnable = object : Runnable {
        override fun run() {
            publishSensorNotification()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = OnnxEngine.getOrCreate(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
        registerPhoneSensors()
        startForeground(NOTIFICATION_ID, buildTelemetryNotification())
        handler.postDelayed(updateRunnable, 1500L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val remoteInputText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_QUICK_PROMPT)?.toString()
            val directCmd = intent.getStringExtra(EXTRA_COMMAND_TEXT)
            val cmd = when {
                !remoteInputText.isNullOrBlank() -> remoteInputText
                !directCmd.isNullOrBlank() -> directCmd
                else -> null
            }

            if (cmd != null) {
                lastCommandResult = engine.processPromptToCommand(cmd)
            } else if (intent.action == ACTION_REFRESH) {
                lastCommandResult = "Refreshed from phone sensors (k=${HeuristicEngine.K_SCALE})"
            }
        }
        publishSensorNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        when (ev.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = ev.values.getOrElse(0) { 0f }
                val y = ev.values.getOrElse(1) { 0f }
                val z = ev.values.getOrElse(2) { 0f }
                accelMag = sqrt((x * x) + (y * y) + (z * z))
            }
            Sensor.TYPE_GYROSCOPE -> {
                val x = ev.values.getOrElse(0) { 0f }
                val y = ev.values.getOrElse(1) { 0f }
                val z = ev.values.getOrElse(2) { 0f }
                gyroNorm = sqrt((x * x) + (y * y) + (z * z))
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = ev.values.getOrElse(0) { 0f }
                val y = ev.values.getOrElse(1) { 0f }
                val z = ev.values.getOrElse(2) { 0f }
                magNorm = sqrt((x * x) + (y * y) + (z * z))
            }
            Sensor.TYPE_LIGHT -> {
                luxLevel = ev.values.firstOrNull() ?: luxLevel
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerPhoneSensors() {
        val sensors = listOfNotNull(
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        )
        for (s in sensors) {
            sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lexi-Q Sensor Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live phone sensor telemetry in the notification pane"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun publishSensorNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildTelemetryNotification())
    }

    private fun buildTelemetryNotification(): Notification {
        val hScore = HeuristicEngine.masterHeuristicScore(
            state = floatArrayOf(accelMag, gyroNorm, magNorm),
            target = floatArrayOf(9.81f, 0.0f, 45.0f),
            mean = floatArrayOf(9.81f, 0.1f, 45.0f),
            variance = floatArrayOf(2.0f, 1.0f, 15.0f),
            probs = floatArrayOf(0.5f, 0.3f, 0.2f),
            priorProbs = floatArrayOf(0.34f, 0.33f, 0.33f),
            velocity = gyroNorm
        )
        val pidOut = pid.computeComplianceAdjustment(1.0f, 0.98f, 0.99f)

        val telemetryLine =
            "ACC:${"%.2f".format(accelMag)} m/s² | GYR:${"%.2f".format(gyroNorm)} | MAG:${"%.1f".format(magNorm)}µT | LUX:${"%.0f".format(luxLevel)}"
        val metricLine =
            "H(k=0.666): ${"%.3f".format(hScore)} • PID: ${"%.2f".format(pidOut)} • ${engine.currentIntelligenceTier.label}"

        val refreshIntent = Intent(this, SensorNotificationTelemetryService::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getService(
            this,
            101,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_QUICK_PROMPT)
            .setLabel("Command (e.g. tier 2, status, telemetry)")
            .build()

        val replyIntent = Intent(this, SensorNotificationTelemetryService::class.java).apply {
            action = ACTION_COMMAND
        }
        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPending = PendingIntent.getService(this, 102, replyIntent, replyFlags)
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Cmd",
            replyPending
        ).addRemoteInput(remoteInput).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Lexi-Q Sensor Telemetry")
            .setContentText("$telemetryLine • $metricLine")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$telemetryLine\n$metricLine\n$lastCommandResult")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_popup_sync, "Sync", refreshPending)
            .addAction(replyAction)
            .build()
    }
}
