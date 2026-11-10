package com.screenprank

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.provider.Settings
import androidx.core.content.ContextCompat

class PrankTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val action = if (OverlayService.active) OverlayService.ACTION_STOP else OverlayService.ACTION_START
        if (action == OverlayService.ACTION_START && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            val i = Intent(this, OverlayService::class.java).setAction(action)
            if (action == OverlayService.ACTION_START && Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, i)
            else startService(i)
        }
        qsTile?.state = if (action == OverlayService.ACTION_START) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    private fun updateTileState() {
        qsTile?.state = if (OverlayService.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.label = if (OverlayService.active) "עצור מסך מופרע" else "מסך מופרע"
        qsTile?.updateTile()
    }
}
