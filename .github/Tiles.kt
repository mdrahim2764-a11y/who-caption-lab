package com.amar.toolkit

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TorchTile : TileService() {
    override fun onStartListening() = update()

    override fun onClick() {
        Torch.toggle(this)
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (Torch.isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "টর্চ"
        t.updateTile()
    }
}

class TimeoutTile : TileService() {
    private val options = intArrayOf(30_000, 120_000, 600_000)

    override fun onStartListening() = update()

    override fun onClick() {
        if (!Settings.System.canWrite(this)) {
            openSettings()
            return
        }
        val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        val idx = options.indexOfFirst { it > cur }.let { if (it < 0) 0 else it }
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, options[idx])
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        t.label = if (cur >= 60_000) "স্ক্রিন ${cur / 60_000} মিনিট" else "স্ক্রিন ${cur / 1000} সেকেন্ড"
        t.state = if (cur >= 600_000) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openSettings() {
        val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(i)
        }
    }
}
