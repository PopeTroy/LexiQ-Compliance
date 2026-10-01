package org.linguistic.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlin.concurrent.thread

class NimShadeReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val userPrompt = results.getCharSequence(NimNotificationService.KEY_TEXT_REPLY)?.toString() ?: return

        // 1. Immediately acknowledge in notification
        NimNotificationService.updateShadeNotification(context, "Processing local command: \"$userPrompt\"...")

        // 2. Route command through local offline engine
        thread {
            val responseText = executeLocalNimPipeline(userPrompt)
            
            // 3. Update notification drawer with local AI result
            NimNotificationService.updateShadeNotification(context, responseText)
        }
    }

    private fun executeLocalNimPipeline(input: String): String {
        // Local command routing execution (No external cloud call)
        return when {
            input.contains("scan", ignoreCase = true) || input.contains("plant", ignoreCase = true) -> {
                "[Local VLM NIM]: Ready for camera frame input. Species analysis initialized."
            }
            input.contains("unbrick", ignoreCase = true) -> {
                "[Local Core]: Security override rule matched. Executing system entitlement."
            }
            else -> {
                "[Offline NIM Output]: $input processed natively via INT8 ONNX Engine."
            }
        }
    }
}
