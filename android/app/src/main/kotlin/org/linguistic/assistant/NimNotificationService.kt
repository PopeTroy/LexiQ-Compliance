package org.linguistic.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

class NimNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "nim_offline_shade_channel"
        const val NOTIF_ID = 1001
        const val KEY_TEXT_REPLY = "key_nim_user_input"

        fun updateShadeNotification(context: Context, statusText: String, imagePreview: Bitmap? = null) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel(context, notificationManager)

            // Setup Inline Reply Input (Grok-like shade action)
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Ask Local NIM...")
                .build()

            val replyIntent = Intent(context, NimShadeReplyReceiver::class.java)
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Execute Command",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("LexiQ Offline NIM Assistant")
                .setContentText(statusText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .addAction(replyAction)

            imagePreview?.let {
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(it).setSummaryText(statusText))
            }

            notificationManager.notify(NOTIF_ID, builder.build())
        }

        private fun createNotificationChannel(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Offline Local NIM Microservices",
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?) = null
}
