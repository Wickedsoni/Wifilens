package com.wickedcoder.wifilens.feature.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.toWifiBand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/** While the Quick Settings panel is open the tile refreshes this often (the connection read is cheap). */
private const val TILE_REFRESH_MS = 3_000L

/**
 * Quick Settings tile: signal and band in the subtitle, active while on Wi-Fi; tapping opens WifiLens. Like the
 * widget, it shows no network name (that would be location data in the background).
 */
@AndroidEntryPoint
class SignalTileService : TileService() {
    @Inject lateinit var connection: WifiConnectionRepository

    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = MainScope().also { scope ->
            connection.observeLive(TILE_REFRESH_MS).onEach(::render).launchIn(scope)
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun { openApp() }
    }

    private fun render(info: WifiConnectionInfo) {
        val tile = qsTile ?: return
        val connected = info as? WifiConnectionInfo.Connected
        tile.state = if (connected != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = connected?.let { getString(R.string.widget_tile_subtitle, it.rssi, it.frequencyMhz.toWifiBand()) }
                ?: getString(R.string.widget_not_on_wifi)
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent overload is the only one before API 34
    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
