package com.wickedcoder.wifilens.feature.analyze.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.wifi.WifiConnectionInfo
import com.wickedcoder.wifilens.core.wifi.WifiScanResult
import com.wickedcoder.wifilens.core.wifi.WifiScanUpdate
import com.wickedcoder.wifilens.core.wifi.maskBssid
import com.wickedcoder.wifilens.core.wifi.toSecurityLabel
import com.wickedcoder.wifilens.core.wifi.toWifiBand
import com.wickedcoder.wifilens.core.wifi.toWifiChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Android allows a foreground app ~4 scans per rolling 2 minutes (API 28+). */
private const val SCAN_QUOTA = 4
private const val SCAN_QUOTA_WINDOW_MS = 120_000L

/** Used when the platform refuses a scan but we haven't spent the quota ourselves (another app did). */
private const val UNKNOWN_THROTTLE_SECONDS = 30

/** A scan we started should broadcast results within seconds; don't sit on "SCANNING" forever. */
private const val SCAN_RESULT_TIMEOUT_MS = 15_000L

/**
 * MVI ViewModel for the Analyze tab (Networks + Spectrum). Wi-Fi access goes through injected
 * function references ([wifiConnectionFlow], [wifiScanFlow], [startScan], [currentScanUpdate])
 * rather than a concrete `Context`/`WifiManager` dependency, so this class has no direct Android
 * framework dependency of its own — a fake `Flow`/lambda is enough to exercise it, with no
 * Robolectric or emulator required.
 */
class AnalyzeViewModel(
    private val wifiConnectionFlow: () -> Flow<WifiConnectionInfo>,
    private val wifiScanFlow: () -> Flow<WifiScanUpdate>,
    /** One `WifiManager.startScan()`; false when the platform refuses it. */
    private val startScan: () -> Boolean,
    /** Re-reads Wi-Fi/Location/results right now; used to re-check when the app returns to the foreground. */
    private val currentScanUpdate: () -> WifiScanUpdate,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyzeState())
    val state: StateFlow<AnalyzeState> = _state.asStateFlow()

    private var throttleJob: Job? = null
    private var scanTimeoutJob: Job? = null

    /** Times of scans *we* got the platform to accept, newest last — drives the throttle ETA. */
    private val acceptedScanTimes = ArrayDeque<Long>()

    /** When the last scan result arrived (system-triggered or ours); null until the first one. */
    private var lastScanAt: Long? = null

    /**
     * True until we've kicked one scan since start-up or since a blocker (Wi-Fi off, Location off)
     * cleared. Right after either happens the platform's cache is empty or stale, and a passive
     * listener could wait a long time for the system to scan on its own.
     */
    private var needsKickScan = true

    init {
        // Two independent collectors, not `combine`: combine re-applies the last scan update on
        // every connection change (RSSI ticks), which would keep resetting the scan status —
        // including a running throttle countdown — back to Idle.
        wifiConnectionFlow()
            .onEach { connectionInfo ->
                val connection = connectionInfo.toDomain()
                _state.update { current ->
                    val networksTab = current.networksTab.copy(connection = connection)
                    current.copy(
                        networksTab = networksTab,
                        spectrumTab = current.spectrumTab.withDerivedData(
                            networks = networksTab.networks,
                            connected = (connection as? ConnectionStatus.Connected)?.network,
                        ),
                    )
                }
            }
            .launchIn(viewModelScope)

        wifiScanFlow()
            .onEach(::onScanUpdate)
            .launchIn(viewModelScope)

        // Only the Idle status shows an age, so the ticker leaves every other status alone.
        flow {
            while (true) {
                emit(Unit)
                delay(1_000)
            }
        }
            .map { scanAgeSeconds() }
            .onEach { seconds ->
                _state.update { current ->
                    val status = current.networksTab.scanStatus
                    if (status is ScanStatus.Idle && status.lastScanAgoSeconds != seconds) {
                        current.copy(networksTab = current.networksTab.copy(scanStatus = ScanStatus.Idle(seconds)))
                    } else {
                        current
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun scanAgeSeconds(): Int? =
        lastScanAt?.let { ((nowMillis() - it) / 1000).toInt().coerceAtLeast(0) }

    private fun idleStatus() = ScanStatus.Idle(lastScanAgoSeconds = scanAgeSeconds())

    private fun onScanUpdate(update: WifiScanUpdate) {
        when (update) {
            is WifiScanUpdate.Results -> {
                scanTimeoutJob?.cancel()
                // Only a real scan resets the age. The seed read at start-up (and the re-read after
                // Wi-Fi/Location comes back) is the platform's cache and may be minutes old, so it
                // leaves lastScanAt alone — null keeps showing "NO SCAN YET" until a scan lands.
                if (update.fresh) lastScanAt = nowMillis()
                val throttled = throttleJob?.isActive == true
                applyScan(
                    networks = update.results.toDomain(),
                    // A finished scan doesn't lift the quota: leave a running countdown alone.
                    status = if (throttled) null else idleStatus(),
                )
                if (needsKickScan && !throttled) {
                    needsKickScan = false
                    requestScan(userInitiated = false)
                }
            }

            // Scan availability also drops while Wi-Fi/Location is off; those states win.
            WifiScanUpdate.Throttled -> if (!isBlocked()) startThrottleCountdown(SCAN_QUOTA_WINDOW_MS.toInt() / 1000)

            WifiScanUpdate.LocationDisabled -> blocked(ScanStatus.LocationOff)
            WifiScanUpdate.WifiOff -> blocked(ScanStatus.WifiOff)
        }
    }

    private fun blocked(status: ScanStatus) {
        cancelScanJobs()
        needsKickScan = true
        applyScan(networks = emptyList(), status = status)
    }

    private fun isBlocked(): Boolean {
        val status = _state.value.networksTab.scanStatus
        return status is ScanStatus.LocationOff || status is ScanStatus.WifiOff
    }

    /**
     * The app came back to the foreground: re-check Wi-Fi/Location/permission so a change made
     * while it was away (or a missed broadcast) doesn't leave a stale blocker on screen. Only acts
     * when something changed — cached results alone are not treated as a fresh scan.
     */
    fun onResumed() {
        when (val update = currentScanUpdate()) {
            WifiScanUpdate.LocationDisabled, WifiScanUpdate.WifiOff -> onScanUpdate(update)
            is WifiScanUpdate.Results -> if (isBlocked()) onScanUpdate(update) // blocker cleared
            WifiScanUpdate.Throttled -> Unit
        }
    }

    /** User tapped refresh: request one scan; results arrive via the scan flow's receiver. */
    fun onRefreshScan() = requestScan(userInitiated = true)

    /**
     * [userInitiated] = false is the automatic scan after start-up / a cleared blocker: if the
     * platform refuses it (radio still coming up), quietly go back to idle instead of showing a
     * "throttled" countdown the user never caused.
     */
    private fun requestScan(userInitiated: Boolean) {
        if (!_state.value.networksTab.scanStatus.canRefresh) return
        setScanStatus(ScanStatus.Scanning)

        if (startScan()) {
            acceptedScanTimes.addLast(nowMillis())
            while (acceptedScanTimes.size > SCAN_QUOTA) acceptedScanTimes.removeFirst()

            scanTimeoutJob?.cancel()
            scanTimeoutJob = viewModelScope.launch {
                delay(SCAN_RESULT_TIMEOUT_MS)
                if (_state.value.networksTab.scanStatus is ScanStatus.Scanning) {
                    setScanStatus(idleStatus())
                }
            }
        } else if (userInitiated) {
            startThrottleCountdown(estimateThrottleSeconds())
        } else {
            setScanStatus(idleStatus())
        }
    }

    private fun estimateThrottleSeconds(): Int {
        if (acceptedScanTimes.size < SCAN_QUOTA) return UNKNOWN_THROTTLE_SECONDS
        val freeAtMillis = acceptedScanTimes.first() + SCAN_QUOTA_WINDOW_MS
        return ((freeAtMillis - nowMillis() + 999) / 1000).toInt().coerceAtLeast(5)
    }

    private fun startThrottleCountdown(seconds: Int) {
        cancelScanJobs()
        throttleJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                setScanStatus(ScanStatus.Throttled(nextScanEtaSeconds = remaining))
                delay(1_000)
                remaining--
            }
            setScanStatus(idleStatus())
        }
    }

    private fun cancelScanJobs() {
        throttleJob?.cancel()
        scanTimeoutJob?.cancel()
    }

    private fun setScanStatus(status: ScanStatus) {
        _state.update { it.copy(networksTab = it.networksTab.copy(scanStatus = status)) }
    }

    /** [status] == null keeps the current scan status and only swaps the network list. */
    private fun applyScan(networks: List<ScannedNetwork>, status: ScanStatus?) {
        _state.update { current ->
            val networksTab = current.networksTab.copy(
                networks = networks,
                scanStatus = status ?: current.networksTab.scanStatus,
            )
            current.copy(
                networksTab = networksTab,
                spectrumTab = current.spectrumTab.withDerivedData(
                    networks = networksTab.networks,
                    connected = (networksTab.connection as? ConnectionStatus.Connected)?.network,
                ),
            )
        }
    }

    fun onTabSelected(tab: AnalyzeTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onBandFilterSelected(filter: BandFilter) {
        _state.update { it.copy(networksTab = it.networksTab.copy(bandFilter = filter)) }
    }

    fun onSortSelected(sort: NetworkSort) {
        _state.update { it.copy(networksTab = it.networksTab.copy(sort = sort)) }
    }

    fun onSpectrumBandSelected(band: BandFilter) {
        _state.update { current ->
            current.copy(
                spectrumTab = current.spectrumTab.copy(band = band).withDerivedData(
                    networks = current.networksTab.networks,
                    connected = (current.networksTab.connection as? ConnectionStatus.Connected)?.network,
                ),
            )
        }
    }
}

/** Android wraps SSIDs in quotes and returns "<unknown ssid>" when it may not reveal the name
 * (missing location/nearby-devices permission, or location services off). */
private fun String.toDisplaySsid(): String =
    removeSurrounding("\"").takeUnless { it.isBlank() || it == "<unknown ssid>" } ?: "Connected network"

private fun WifiConnectionInfo.toDomain(): ConnectionStatus = when (this) {
    WifiConnectionInfo.Disconnected -> ConnectionStatus.Disconnected
    is WifiConnectionInfo.Connected -> ConnectionStatus.Connected(
        ConnectedNetwork(
            ssid = ssid.toDisplaySsid(),
            rssiDbm = rssi,
            channel = frequencyMhz.toWifiChannel(),
            band = frequencyMhz.toWifiBand(),
        ),
    )
}

private fun List<WifiScanResult>.toDomain(): List<ScannedNetwork> = map { result ->
    ScannedNetwork(
        ssid = result.ssid,
        bssidMasked = result.bssid.maskBssid(),
        security = result.capabilities.toSecurityLabel(),
        rssiDbm = result.rssi,
        channel = result.frequencyMhz.toWifiChannel(),
        band = result.frequencyMhz.toWifiBand(),
    )
}

private fun SpectrumTabState.withDerivedData(
    networks: List<ScannedNetwork>,
    connected: ConnectedNetwork?,
): SpectrumTabState {
    val connectedChannel = connected?.channel
    val inBand = networks.filter { it.band == band.label }
    val bars = inBand
        .groupBy { it.channel }
        .map { (channel, onChannel) ->
            SpectrumBar(
                channel = channel,
                congestionScore = onChannel.sumOf { rssiToCongestionContribution(it.rssiDbm) }.coerceAtMost(100),
                networkLabels = onChannel.map { it.ssid },
                peakRssiDbm = onChannel.maxOf { it.rssiDbm },
            )
        }
        .sortedBy { it.channel }

    val coChannel = connectedChannel?.let { ch -> inBand.count { it.channel == ch } } ?: 0
    val overlapping = connectedChannel?.let { ch ->
        inBand.count { it.channel != ch && abs(it.channel - ch) <= 2 }
    } ?: 0
    val strongestInterferer = inBand
        .filter { it.channel != connectedChannel }
        .maxOfOrNull { it.rssiDbm }

    return copy(
        bars = bars,
        stats = SpectrumStats(
            coChannelCount = coChannel,
            overlappingCount = overlapping,
            strongestInterfererDbm = strongestInterferer,
        ),
        advice = recommendChannel(networks, band, connected),
    )
}
