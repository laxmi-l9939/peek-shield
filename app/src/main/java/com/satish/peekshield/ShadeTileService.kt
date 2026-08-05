package com.satish.peekshield

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile that toggles the privacy shade on/off.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ShadeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (isShadeRunning()) {
            stopShade()
        } else {
            startShade()
        }
        refreshTile()
    }

    private fun isShadeRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = am.getRunningServices(Int.MAX_VALUE) ?: return false
        return services.any { it.service.className == ShadeService::class.java.name }
    }

    private fun startShade() {
        val intent = Intent(this, ShadeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopShade() {
        val intent = Intent(this, ShadeService::class.java).apply {
            action = ShadeService.ACTION_STOP
        }
        startService(intent)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val running = isShadeRunning()
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (running) "Peek Shield: On" else "Peek Shield"
        tile.updateTile()
    }
}
