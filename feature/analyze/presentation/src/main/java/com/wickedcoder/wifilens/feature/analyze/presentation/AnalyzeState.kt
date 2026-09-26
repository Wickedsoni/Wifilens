package com.wickedcoder.wifilens.feature.analyze.presentation

import com.wickedcoder.wifilens.feature.analyze.domain.BandFilter
import com.wickedcoder.wifilens.feature.analyze.domain.ChannelAdvice
import com.wickedcoder.wifilens.feature.analyze.domain.ConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.ScannedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.SpectrumBar
import com.wickedcoder.wifilens.feature.analyze.domain.SpectrumStats

enum class AnalyzeTab(val label: String) { Networks("Networks"), Spectrum("Spectrum") }

enum class NetworkSort(val label: String) { Signal("Signal"), Channel("Channel") }

/** What the device is currently connected to on Wi-Fi, independent of [ScanStatus] — a scan can be
 * throttled or off while the device stays connected to a network it joined earlier. */
sealed interface ConnectionStatus {
    data object Loading : ConnectionStatus

    data class Connected(val network: ConnectedNetwork) : ConnectionStatus

    data object Disconnected : ConnectionStatus
}

/** Mirrors mockup 1c's four states: actively scanning, throttled, empty result, and the
 * "continue without scanning" gate (no permission / scanning declined). */
sealed interface ScanStatus {
    data object Scanning : ScanStatus

    data class Throttled(val nextScanEtaSeconds: Int) : ScanStatus

    /** [lastScanAgoSeconds] is kept fresh by the ViewModel's 1 s ticker; null = no scan received yet. */
    data class Idle(val lastScanAgoSeconds: Int?) : ScanStatus

    data object NotScanning : ScanStatus

    /** Location permission is granted but the device-wide Location toggle is off, so Android
     * returns no scan results at all. */
    data object LocationOff : ScanStatus

    /** The Wi-Fi radio is off (reachable via "continue without scanning" on the permission gate). */
    data object WifiOff : ScanStatus
}

/** Manual refresh is only offered while idle — not mid-scan, throttled, or unable to scan. */
val ScanStatus.canRefresh: Boolean get() = this is ScanStatus.Idle

/** State for the Networks tab: the connection status banner, scan status, and the filtered/sorted
 * network list ([visibleNetworks] is derived, not stored — [networks] stays the untouched scan
 * result so switching [bandFilter]/[sort] never needs a re-scan). */
data class NetworksTabState(
    val connection: ConnectionStatus = ConnectionStatus.Loading,
    val scanStatus: ScanStatus = ScanStatus.Scanning,
    val networks: List<ScannedNetwork> = emptyList(),
    val bandFilter: BandFilter = BandFilter.All,
    val sort: NetworkSort = NetworkSort.Signal,
) {
    val visibleNetworks: List<ScannedNetwork>
        get() = networks
            .filter { bandFilter == BandFilter.All || it.band == bandFilter.label }
            .let { list ->
                when (sort) {
                    NetworkSort.Signal -> list.sortedByDescending { it.rssiDbm }
                    NetworkSort.Channel -> list.sortedBy { it.channel }
                }
            }
}

/** State for the Spectrum tab: per-channel congestion bars for one band, plus the derived
 * best-channel [advice]. */
data class SpectrumTabState(
    val band: BandFilter = BandFilter.Band24,
    val availableBands: List<BandFilter> = listOf(BandFilter.Band24, BandFilter.Band5),
    val bars: List<SpectrumBar> = emptyList(),
    val stats: SpectrumStats = SpectrumStats(0, 0, null),
    /** Null hides the BEST CHANNEL card: not connected, or no scan data for this band yet. */
    val advice: ChannelAdvice? = null,
)

/** MVI state for the whole Analyze tab: which sub-tab is selected, plus both sub-tabs' state kept
 * side by side rather than one being torn down on switch, so flipping tabs never re-triggers a scan
 * or a spectrum recompute that's already been done. */
data class AnalyzeState(
    val selectedTab: AnalyzeTab = AnalyzeTab.Networks,
    val networksTab: NetworksTabState = NetworksTabState(),
    val spectrumTab: SpectrumTabState = SpectrumTabState(),
)
