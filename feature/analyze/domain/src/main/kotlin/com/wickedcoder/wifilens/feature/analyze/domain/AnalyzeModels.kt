package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.WifiSecurity

/** Which band a spectrum or list is limited to. [label] matches [ScannedNetwork.band]; it's a key, not UI text. */
enum class BandFilter(val label: String) { All("all"), Band24("2.4"), Band5("5"), Band6("6") }

/** The network the phone is currently joined to. */
data class ConnectedNetwork(
    /** Null when Android hides the name (no location permission, or location services off). */
    val ssid: String?,
    val rssiDbm: Int,
    val channel: Int,
    val band: String,
    val bandwidthMhz: Int? = null,
    val standard: String? = null,
)

/** One access point seen in a scan, with the BSSID already masked for display. */
data class ScannedNetwork(
    val ssid: String,
    val bssidMasked: String,
    val security: WifiSecurity,
    val rssiDbm: Int,
    val channel: Int,
    val band: String,
    /** Stable, unique list key (the raw BSSID for real scans); never shown, the UI displays [bssidMasked]. */
    val id: String = "$ssid|$bssidMasked|$channel",
)

/** One channel's bar in the spectrum chart. */
data class SpectrumBar(
    val channel: Int,
    val congestionScore: Int,
    val networkLabels: List<String>,
    val peakRssiDbm: Int,
)

data class SpectrumStats(
    val coChannelCount: Int,
    val overlappingCount: Int,
    val strongestInterfererDbm: Int?,
)

/** Everything the Spectrum tab derives from a scan for one band. */
data class SpectrumSummary(
    val bars: List<SpectrumBar>,
    val stats: SpectrumStats,
    /** Null hides the BEST CHANNEL card: not connected, or no scan data for this band yet. */
    val advice: ChannelAdvice?,
)
