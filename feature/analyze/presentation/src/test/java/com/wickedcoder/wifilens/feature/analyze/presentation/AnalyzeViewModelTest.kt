package com.wickedcoder.wifilens.feature.analyze.presentation

import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import com.wickedcoder.wifilens.core.model.maskBssid
import com.wickedcoder.wifilens.feature.analyze.domain.BandFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives [AnalyzeViewModel] through its injected Wi-Fi function references, so no Android framework is
 * involved. Time comes from the test scheduler. The VM's 1 s age ticker never finishes, so these tests
 * use `runCurrent`/`advanceTimeBy` and never `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val connection = MutableStateFlow<WifiConnectionInfo>(WifiConnectionInfo.Disconnected)
    private val scanUpdates = MutableSharedFlow<WifiScanUpdate>(replay = 1, extraBufferCapacity = 8)
    private var acceptScans = true
    private var scansStarted = 0
    private var currentUpdate: WifiScanUpdate = WifiScanUpdate.Results(emptyList(), 0)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel(): AnalyzeViewModel {
        val vm = AnalyzeViewModel(
            scanRepository = object : WifiScanRepository {
                override fun observe() = scanUpdates

                override fun startScan(): Boolean {
                    scansStarted++
                    return acceptScans
                }

                override fun currentUpdate() = currentUpdate
            },
            connectionRepository = object : WifiConnectionRepository {
                override fun observe() = connection
            },
            nowMillis = { testScheduler.currentTime },
        )
        runCurrent()
        return vm
    }

    /** Runs [block] with a fresh ViewModel and cancels its scope afterwards: the 1 s age ticker never ends, so
     * without this `runTest` would wait for it forever. */
    private fun vmTest(block: suspend TestScope.(AnalyzeViewModel) -> Unit) = runTest(dispatcher) {
        val vm = newViewModel()
        try {
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun network(ssid: String, bssid: String = "aa:bb:cc:dd:ee:ff", rssi: Int = -60, freq: Int = 2437) =
        WifiScanResult(ssid = ssid, bssid = bssid, rssi = rssi, frequencyMhz = freq, capabilities = "[WPA2-PSK-CCMP][ESS]")

    private fun TestScope.emit(update: WifiScanUpdate) {
        scanUpdates.tryEmit(update)
        runCurrent()
    }

    private fun AnalyzeViewModel.scanStatus() = state.value.networksTab.scanStatus

    // ---- connection ---------------------------------------------------------------------------

    @Test
    fun `connected network shows its ssid without quotes`() = vmTest { vm ->

        connection.value = WifiConnectionInfo.Connected(ssid = "\"Home\"", rssi = -50, linkSpeedMbps = 866, frequencyMhz = 5180)
        runCurrent()

        val status = vm.state.value.networksTab.connection as ConnectionStatus.Connected
        assertEquals("Home", status.network.ssid)
        assertEquals(36, status.network.channel)
    }

    @Test
    fun `hidden or unknown ssid falls back to a generic name`() = vmTest { vm ->

        connection.value = WifiConnectionInfo.Connected(ssid = "<unknown ssid>", rssi = -50, linkSpeedMbps = 100, frequencyMhz = 2412)
        runCurrent()

        val status = vm.state.value.networksTab.connection as ConnectionStatus.Connected
        assertEquals("Connected network", status.network.ssid)
    }

    // ---- scan results -------------------------------------------------------------------------

    @Test
    fun `results are mapped with masked bssid, channel and band`() = vmTest { vm ->

        emit(WifiScanUpdate.Results(listOf(network("Cafe", bssid = "10:5a:17:12:34:58", freq = 5180)), 0, fresh = true))

        val net = vm.state.value.networksTab.networks
            .single()
        assertEquals("10:5a:17:12:34:58".maskBssid(), net.bssidMasked)
        assertEquals(36, net.channel)
        assertEquals("5", net.band)
    }

    @Test
    fun `first results trigger exactly one automatic scan`() = vmTest {
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false))
        emit(WifiScanUpdate.Results(listOf(network("A")), 1, fresh = true))
        emit(WifiScanUpdate.Results(listOf(network("A")), 2, fresh = true))

        assertEquals(1, scansStarted)
    }

    @Test
    fun `cached results fill the list and start the automatic scan`() = vmTest { vm ->

        emit(WifiScanUpdate.Results(listOf(network("Cafe")), timestampMillis = 0, fresh = false))

        assertEquals(
            listOf("Cafe"),
            vm.state.value.networksTab.networks
                .map { it.ssid },
        )
        assertEquals(ScanStatus.Scanning, vm.scanStatus())
    }

    @Test
    fun `a fresh result puts the status back to idle and starts the age counter`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false)) // triggers the automatic scan
        assertEquals(ScanStatus.Scanning, vm.scanStatus())

        emit(WifiScanUpdate.Results(listOf(network("A")), 1, fresh = true))
        assertEquals(ScanStatus.Idle(0), vm.scanStatus())

        advanceTimeBy(5_000)
        runCurrent() // advanceTimeBy stops just before tasks due exactly at the target time
        assertEquals(ScanStatus.Idle(5), vm.scanStatus())
    }

    // ---- blockers -----------------------------------------------------------------------------

    @Test
    fun `wifi off clears the list and shows the blocker`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = true))

        emit(WifiScanUpdate.WifiOff)

        assertEquals(ScanStatus.WifiOff, vm.scanStatus())
        assertTrue(
            vm.state.value.networksTab.networks
                .isEmpty(),
        )
    }

    @Test
    fun `location off shows its own blocker`() = vmTest { vm ->

        emit(WifiScanUpdate.LocationDisabled)

        assertEquals(ScanStatus.LocationOff, vm.scanStatus())
    }

    @Test
    fun `throttle broadcast is ignored while a blocker is showing`() = vmTest { vm ->
        emit(WifiScanUpdate.WifiOff)

        emit(WifiScanUpdate.Throttled)

        assertEquals(ScanStatus.WifiOff, vm.scanStatus())
    }

    @Test
    fun `resuming the app clears a blocker once results are available again`() = vmTest { vm ->
        emit(WifiScanUpdate.WifiOff)

        currentUpdate = WifiScanUpdate.Results(listOf(network("A")), 0)
        vm.onResumed()
        runCurrent()

        assertTrue(vm.scanStatus() !is ScanStatus.WifiOff)
        assertEquals(
            listOf("A"),
            vm.state.value.networksTab.networks
                .map { it.ssid },
        )
    }

    @Test
    fun `resuming while still blocked keeps the blocker`() = vmTest { vm ->
        emit(WifiScanUpdate.LocationDisabled)

        currentUpdate = WifiScanUpdate.LocationDisabled
        vm.onResumed()
        runCurrent()

        assertEquals(ScanStatus.LocationOff, vm.scanStatus())
    }

    // ---- refresh, throttling, timeout ---------------------------------------------------------

    @Test
    fun `a refused manual refresh starts a throttle countdown`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false)) // automatic scan accepted
        emit(WifiScanUpdate.Results(listOf(network("A")), 1, fresh = true)) // -> idle
        acceptScans = false

        vm.onRefreshScan()
        runCurrent()

        assertEquals(ScanStatus.Throttled(30), vm.scanStatus())
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(ScanStatus.Throttled(27), vm.scanStatus())
    }

    @Test
    fun `countdown ends back at idle`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false))
        emit(WifiScanUpdate.Results(listOf(network("A")), 1, fresh = true))
        acceptScans = false
        vm.onRefreshScan()
        runCurrent()

        advanceTimeBy(31_000)

        assertTrue(vm.scanStatus() is ScanStatus.Idle)
    }

    @Test
    fun `after four accepted scans a refusal predicts the wait from the oldest one`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false)) // automatic scan #1 at t=0
        repeat(3) {
            advanceTimeBy(10_000)
            emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = true)) // result -> idle
            vm.onRefreshScan() // scans #2..#4 at t=10s, 20s, 30s
            runCurrent()
        }
        advanceTimeBy(10_000)
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = true))
        acceptScans = false

        vm.onRefreshScan() // refused at t=40s; quota frees when scan #1 ages out at t=120s
        runCurrent()

        assertEquals(ScanStatus.Throttled(80), vm.scanStatus())
    }

    @Test
    fun `a scan that never reports back returns to idle after the timeout`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false)) // automatic scan accepted
        assertEquals(ScanStatus.Scanning, vm.scanStatus())

        advanceTimeBy(15_001)

        assertTrue(vm.scanStatus() is ScanStatus.Idle)
    }

    @Test
    fun `refresh is ignored while a scan is already running`() = vmTest { vm ->
        emit(WifiScanUpdate.Results(listOf(network("A")), 0, fresh = false)) // auto scan -> Scanning
        val before = scansStarted

        vm.onRefreshScan()
        runCurrent()

        assertEquals(before, scansStarted)
    }

    // ---- filters and sorting ------------------------------------------------------------------

    @Test
    fun `band filter and sort change the visible list only`() = vmTest { vm ->
        emit(
            WifiScanUpdate.Results(
                listOf(
                    network("Weak24", rssi = -80, freq = 2412),
                    network("Strong5", rssi = -40, freq = 5180),
                    network("Mid24", rssi = -60, freq = 2462),
                ),
                0,
                fresh = true,
            ),
        )

        assertEquals(
            listOf("Strong5", "Mid24", "Weak24"),
            vm.state.value.networksTab.visibleNetworks
                .map { it.ssid },
        )

        vm.onBandFilterSelected(BandFilter.Band24)
        assertEquals(
            listOf("Mid24", "Weak24"),
            vm.state.value.networksTab.visibleNetworks
                .map { it.ssid },
        )

        vm.onSortSelected(NetworkSort.Channel)
        assertEquals(
            listOf("Weak24", "Mid24"),
            vm.state.value.networksTab.visibleNetworks
                .map { it.ssid },
        )
        assertEquals("the scan list itself is untouched", 3, vm.state.value.networksTab.networks.size)
    }

    @Test
    fun `tab selection is stored`() = vmTest { vm ->

        vm.onTabSelected(AnalyzeTab.Spectrum)

        assertEquals(AnalyzeTab.Spectrum, vm.state.value.selectedTab)
    }
}
