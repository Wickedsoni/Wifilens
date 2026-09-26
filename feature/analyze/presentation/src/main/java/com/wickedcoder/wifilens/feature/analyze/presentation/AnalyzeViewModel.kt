package com.wickedcoder.wifilens.feature.analyze.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import com.wickedcoder.wifilens.feature.analyze.domain.BandFilter
import com.wickedcoder.wifilens.feature.analyze.domain.BuildSpectrum
import com.wickedcoder.wifilens.feature.analyze.domain.ConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.ScanQuota
import com.wickedcoder.wifilens.feature.analyze.domain.ScannedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.toConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.toScannedNetworks
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
    private val scanRepository: WifiScanRepository,
    private val connectionRepository: WifiConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val buildSpectrum: BuildSpectrum = BuildSpectrum(),
    private val scanQuota: ScanQuota = ScanQuota(),
) : ViewModel() {
    private val _state = MutableStateFlow(AnalyzeState())
    val state: StateFlow<AnalyzeState> = _state.asStateFlow()

    private var throttleJob: Job? = null
    private var scanTimeoutJob: Job? = null

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
        connectionRepository
            .observe()
            .onEach { connectionInfo ->
                val connection = connectionInfo.toConnectionStatus()
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
            }.launchIn(viewModelScope)

        scanRepository
            .observe()
            .onEach(::onScanUpdate)
            .launchIn(viewModelScope)

        // Only the Idle status shows an age, so the ticker leaves every other status alone.
        flow {
            while (true) {
                emit(Unit)
                delay(1_000)
            }
        }.map { scanAgeSeconds() }
            .onEach { seconds ->
                _state.update { current ->
                    val status = current.networksTab.scanStatus
                    if (status is ScanStatus.Idle && status.lastScanAgoSeconds != seconds) {
                        current.copy(networksTab = current.networksTab.copy(scanStatus = ScanStatus.Idle(seconds)))
                    } else {
                        current
                    }
                }
            }.launchIn(viewModelScope)
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
                    networks = update.results.toScannedNetworks(),
                    // A finished scan doesn't lift the quota: leave a running countdown alone.
                    status = if (throttled) null else idleStatus(),
                )
                if (needsKickScan && !throttled) {
                    needsKickScan = false
                    requestScan(userInitiated = false)
                }
            }

            // Scan availability also drops while Wi-Fi/Location is off; those states win.
            WifiScanUpdate.Throttled -> {
                if (!isBlocked()) startThrottleCountdown(ScanQuota.WINDOW_SECONDS)
            }

            WifiScanUpdate.LocationDisabled -> {
                blocked(ScanStatus.LocationOff)
            }
            WifiScanUpdate.WifiOff -> {
                blocked(ScanStatus.WifiOff)
            }
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
        when (val update = scanRepository.currentUpdate()) {
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

        if (scanRepository.startScan()) {
            scanQuota.recordAccepted(nowMillis())

            scanTimeoutJob?.cancel()
            scanTimeoutJob = viewModelScope.launch {
                delay(SCAN_RESULT_TIMEOUT_MS)
                if (_state.value.networksTab.scanStatus is ScanStatus.Scanning) {
                    setScanStatus(idleStatus())
                }
            }
        } else if (userInitiated) {
            startThrottleCountdown(scanQuota.estimateWaitSeconds(nowMillis()))
        } else {
            setScanStatus(idleStatus())
        }
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

    private fun SpectrumTabState.withDerivedData(networks: List<ScannedNetwork>, connected: ConnectedNetwork?): SpectrumTabState =
        buildSpectrum(networks, band, connected).let { copy(bars = it.bars, stats = it.stats, advice = it.advice) }
}

private fun WifiConnectionInfo.toConnectionStatus(): ConnectionStatus = when (this) {
    WifiConnectionInfo.Disconnected -> ConnectionStatus.Disconnected
    is WifiConnectionInfo.Connected -> ConnectionStatus.Connected(toConnectedNetwork())
}
