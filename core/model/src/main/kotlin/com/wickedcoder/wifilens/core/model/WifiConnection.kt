package com.wickedcoder.wifilens.core.model

import kotlinx.coroutines.flow.Flow

/** What's currently connected on Wi-Fi, or [Disconnected]. */
sealed interface WifiConnectionInfo {
    data object Disconnected : WifiConnectionInfo

    data class Connected(
        val ssid: String,
        val rssi: Int,
        val linkSpeedMbps: Int,
        val frequencyMhz: Int,
    ) : WifiConnectionInfo
}

/** Live Wi-Fi connection state. Implemented in `:core:wifi` on top of `ConnectivityManager`. */
interface WifiConnectionRepository {
    /** Emits the current state immediately, then on every change. */
    fun observe(): Flow<WifiConnectionInfo>
}
