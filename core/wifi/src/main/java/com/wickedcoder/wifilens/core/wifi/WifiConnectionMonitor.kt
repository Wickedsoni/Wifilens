package com.wickedcoder.wifilens.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits the current Wi-Fi connection state now and on every change, via a `callbackFlow` wrapping
 * [ConnectivityManager.NetworkCallback] and closed with [awaitClose] to unregister it.
 *
 * The one thing that isn't obvious from the callback API itself: [ConnectivityManager.NetworkCallback.onUnavailable]
 * is documented as reliable only for `requestNetwork()`'s timeout variant, not for
 * `registerNetworkCallback()` as used here. Confirmed on-device — with no active Wi-Fi network,
 * neither `onAvailable` nor `onLost` ever fired, so the flow emitted nothing and the UI was stuck on
 * a loading state forever. The fix is the same seed-emit pattern any callback-driven flow needs:
 * send the current state immediately on subscribe instead of waiting for the platform to tell you.
 */
fun wifiConnectionFlow(context: Context): Flow<WifiConnectionInfo> = callbackFlow {
    val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun connectedInfo(): WifiConnectionInfo {
        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            caps?.transportInfo as? WifiInfo // safe cast: transportInfo could be VpnTransportInfo etc., `as` would crash
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }

        return wifiInfo?.let { info ->
            WifiConnectionInfo.Connected(
                ssid = info.ssid,
                rssi = info.rssi,
                linkSpeedMbps = info.linkSpeed,
                frequencyMhz = info.frequency,
            )
        } ?: WifiConnectionInfo.Disconnected
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(connectedInfo())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(connectedInfo())
        }

        override fun onLost(network: Network) {
            trySend(WifiConnectionInfo.Disconnected)
        }

        override fun onUnavailable() {
            trySend(WifiConnectionInfo.Disconnected)
        }
    }

    val request = NetworkRequest
        .Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()
    connectivityManager.registerNetworkCallback(request, callback)

    // registerNetworkCallback's onUnavailable() is NOT guaranteed to fire (that's only reliable
    // for requestNetwork()/timeout variants) — confirmed on-device: with no active Wi-Fi network,
    // neither onAvailable nor onLost ever fired, so the flow emitted nothing and the UI was stuck
    // on ConnectionStatus.Loading forever. Send the current state immediately instead of waiting.
    trySend(connectedInfo())

    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
}
