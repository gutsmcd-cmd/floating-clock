package io.github.gutsmcd.floatingclock

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: show / hide the floating clock. */
class ClockTileService : TileService() {

    private val listener: () -> Unit = { updateTile() }

    override fun onStartListening() {
        super.onStartListening()
        ClockController.addListener(listener)
        updateTile()
    }

    override fun onStopListening() {
        ClockController.removeListener(listener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!ClockController.canDraw(this)) { openApp(); return }
        // The overlay is drawn first; a visible overlay lets the foreground service start.
        ClockController.toggle(this)
        updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 4, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        } catch (_: Exception) { }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val loc = Prefs.localized(this)
        tile.label = loc.getString(R.string.tile_label)
        tile.state = if (ClockController.isShowing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = loc.getString(if (ClockController.isShowing) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }
}
