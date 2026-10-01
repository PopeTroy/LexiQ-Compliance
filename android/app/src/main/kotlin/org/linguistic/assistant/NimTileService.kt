package org.linguistic.assistant

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NimTileService : TileService() {

    override fun onClick() {
        super.onClick()
        
        // Update notification shade state
        NimNotificationService.updateShadeNotification(
            this,
            "Vision NIM Listening: Point camera at flora/fauna or type a command below."
        )

        // Collapse Quick Settings panel to reveal the interactive notification
        val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        sendBroadcast(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "LexiQ Local NIM"
            updateTile()
        }
    }
}
