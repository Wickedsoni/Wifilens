package com.wickedcoder.wifilens.feature.analyze.domain

/** Which band a spectrum or list is limited to. [label] matches [ScannedNetwork.band]. */
enum class BandFilter(val label: String) { All("All"), Band24("2.4"), Band5("5"), Band6("6") }

/** The network the phone is currently joined to. */
data class ConnectedNetwork(
    val ssid: String,
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
    val security: String,
    val rssiDbm: Int,
    val channel: Int,
    val band: String,
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
