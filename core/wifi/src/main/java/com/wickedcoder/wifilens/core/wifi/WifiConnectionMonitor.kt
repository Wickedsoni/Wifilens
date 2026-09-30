package com.wickedcoder.wifilens.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow

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

    fun connectedInfo(): WifiConnectionInfo = readConnection(connectivityManager, wifiManager)

    val callback = ConnectionCallback.create(
        onCapabilities = { capabilities ->
            // On API 31+ only these capabilities carry the SSID (B-55); getNetworkCapabilities() redacts it.
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (capabilities.transportInfo as? WifiInfo)?.toConnectionInfo()
            } else {
                null
            }
            trySend(info ?: connectedInfo())
        },
        onAvailable = { trySend(connectedInfo()) },
        onGone = { trySend(WifiConnectionInfo.Disconnected) },
    )

    // registerNetworkCallback's onUnavailable() is NOT guaranteed to fire (that's only reliable
    // for requestNetwork()/timeout variants) — confirmed on-device: with no active Wi-Fi network,
    // neither onAvailable nor onLost ever fired, so the flow emitted nothing and the UI was stuck
    // on ConnectionStatus.Loading forever. Send the current state immediately instead of waiting.
    // Sent before registering, so the callback's first (unredacted) reading replaces it, not the reverse.
    trySend(connectedInfo())

    val request = NetworkRequest
        .Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()
    connectivityManager.registerNetworkCallback(request, callback)

    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
}

/** The connection as Android reports it right now (API 31+ via NetworkCapabilities, older via WifiManager). */
private fun readConnection(connectivityManager: ConnectivityManager, wifiManager: WifiManager): WifiConnectionInfo {
    val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        caps?.transportInfo as? WifiInfo // safe cast: transportInfo could be VpnTransportInfo etc., `as` would crash
    } else {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo
    }
    return wifiInfo?.toConnectionInfo() ?: WifiConnectionInfo.Disconnected
}

private fun WifiInfo.toConnectionInfo(): WifiConnectionInfo =
    WifiConnectionInfo.Connected(
        ssid = ssid,
        rssi = rssi,
        linkSpeedMbps = linkSpeed,
        frequencyMhz = frequency,
        bssid = bssid?.takeUnless { it == REDACTED_BSSID },
    )

/**
 * On API 31+ asks for SSID/BSSID in the capabilities it receives ([FLAG_INCLUDE_LOCATION_INFO]); without it Android
 * redacts them. The flags constructor doesn't exist below API 31, hence the two paths.
 */
private class ConnectionCallback : ConnectivityManager.NetworkCallback {
    private val onCapabilities: (NetworkCapabilities) -> Unit
    private val onAvailable: () -> Unit
    private val onGone: () -> Unit

    private constructor(
        onCapabilities: (NetworkCapabilities) -> Unit,
        onAvailable: () -> Unit,
        onGone: () -> Unit,
    ) : super() {
        this.onCapabilities = onCapabilities
        this.onAvailable = onAvailable
        this.onGone = onGone
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private constructor(
        flags: Int,
        onCapabilities: (NetworkCapabilities) -> Unit,
        onAvailable: () -> Unit,
        onGone: () -> Unit,
    ) : super(flags) {
        this.onCapabilities = onCapabilities
        this.onAvailable = onAvailable
        this.onGone = onGone
    }

    override fun onAvailable(network: Network) = onAvailable()

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onCapabilities(capabilities)

    override fun onLost(network: Network) = onGone()

    override fun onUnavailable() = onGone()

    companion object {
        fun create(
            onCapabilities: (NetworkCapabilities) -> Unit,
            onAvailable: () -> Unit,
            onGone: () -> Unit,
        ): ConnectionCallback =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ConnectionCallback(FLAG_INCLUDE_LOCATION_INFO, onCapabilities, onAvailable, onGone)
            } else {
                ConnectionCallback(onCapabilities, onAvailable, onGone)
            }
    }
}

/** What Android returns instead of the real BSSID when the caller may not see it. */
private const val REDACTED_BSSID = "02:00:00:00:00:00"

/**
 * [readConnection] every [periodMillis] for live readouts. RSSI isn't location data, so this works for the signal
 * value even when Android redacts the SSID/BSSID; it never triggers a Wi-Fi scan.
 */
fun wifiConnectionPollFlow(context: Context, periodMillis: Long): Flow<WifiConnectionInfo> = flow {
    val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    while (true) {
        emit(readConnection(connectivityManager, wifiManager))
        delay(periodMillis)
    }
}
