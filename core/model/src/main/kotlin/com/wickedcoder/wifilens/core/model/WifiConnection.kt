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
        /** Connected access point; null when Android redacts it (no location permission) or it's unknown. */
        val bssid: String? = null,
    ) : WifiConnectionInfo
}

/** Live Wi-Fi connection state. Implemented in `:core:wifi` on top of `ConnectivityManager`. */
interface WifiConnectionRepository {
    /** Emits the current state immediately, then on every change. */
    fun observe(): Flow<WifiConnectionInfo>

    /**
     * Current state re-read every [periodMillis], for live readouts (signal meter, walk survey). Reading the
     * connection doesn't use Android's Wi-Fi scan quota. Defaults to [observe] for implementations without polling.
     */
    fun observeLive(periodMillis: Long = 1_000): Flow<WifiConnectionInfo> = observe()
}
