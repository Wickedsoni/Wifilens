package com.wickedcoder.wifilens.feature.diagnose.presentation

import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.ThemeMode
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.feature.diagnose.domain.AnalyzeCoverage
import com.wickedcoder.wifilens.feature.diagnose.domain.DiagnoseRepository
import com.wickedcoder.wifilens.feature.diagnose.domain.FindBestRouterSpot
import com.wickedcoder.wifilens.feature.diagnose.domain.MoveRouter
import com.wickedcoder.wifilens.feature.diagnose.domain.ObservePlanContext
import com.wickedcoder.wifilens.feature.diagnose.domain.PlanContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnoseViewModelTest {
    private companion object {
        const val TEARDOWN_ATTEMPTS = 200
        const val TEARDOWN_POLL_MS = 10L
    }

    private val dispatcher = StandardTestDispatcher()

    private val repository = FakeDiagnoseRepository()
    private val created = mutableListOf<DiagnoseViewModel>()

    // Tests read like before (`router.value = ...`, `devices.value = ...`) but write into the fake repository.
    private val router = object {
        var value: Vec2?
            get() = repository.context.value.routerPos
            set(v) {
                repository.context.value = repository.context.value.copy(routerPos = v)
            }
    }
    private val devices = object {
        var value: List<DevicePin>
            get() = repository.context.value.devicePins
            set(v) {
                repository.context.value = repository.context.value.copy(devicePins = v)
            }
    }
    private val wifi = MutableStateFlow<WifiConnectionInfo>(WifiConnectionInfo.Disconnected)
    private var speedUpdates: () -> Flow<SpeedTestUpdate> = { flowOf(SpeedTestUpdate.Finished(100f)) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        // Coverage and the optimizer run on Dispatchers.Default (real threads) and hop back to Main when done.
        // Cancel each ViewModel and keep driving the test scheduler until its work has finished, so nothing can
        // resume on Dispatchers.Main after it is reset and leak an exception into the next test. (Blocking here
        // with runBlocking would deadlock: the work needs the test dispatcher to run in order to complete.)
        created.forEach { vm ->
            val job = vm.viewModelScope.coroutineContext[Job] ?: return@forEach
            job.cancel()
            repeat(TEARDOWN_ATTEMPTS) {
                dispatcher.scheduler.advanceUntilIdle()
                if (job.isCompleted) return@forEach
                Thread.sleep(TEARDOWN_POLL_MS)
            }
        }
        created.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): DiagnoseViewModel {
        val vm = DiagnoseViewModel(
            observePlanContext = ObservePlanContext(repository),
            settingsRepository = FakeSettings(),
            connectionRepository = FakeConnectionRepository(wifi),
            speedTestRepository = FakeSpeedTestRepository { speedUpdates() },
            analyzeCoverage = AnalyzeCoverage(),
            findBestRouterSpot = FindBestRouterSpot(),
            moveRouter = MoveRouter(repository),
        )
        created += vm
        advanceUntilIdle()
        return vm
    }

    /** Coverage math runs on Dispatchers.Default (real threads), so wait for it in real time. */
    private suspend fun DiagnoseViewModel.awaitState(predicate: (DiagnoseState) -> Boolean): DiagnoseState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { state.first(predicate) } }

    /** Like [awaitState] but also drives the test dispatcher, for work that hops between Default and Main. */
    private suspend fun TestScope.settle(vm: DiagnoseViewModel, predicate: (DiagnoseState) -> Boolean): DiagnoseState {
        repeat(200) {
            advanceUntilIdle()
            if (predicate(vm.state.value)) return vm.state.value
            withContext(Dispatchers.Default) { kotlinx.coroutines.delay(25) }
        }
        throw AssertionError("state never satisfied the condition: ${vm.state.value}")
    }

    private fun seedOpenPlan(size: Int = 10) {
        repository.context.value = repository.context.value.copy(
            plan = GridPlan(size, size, List(size * size) { CellType.Floor(1) }),
            roomNames = mapOf(1 to "Living room"),
        )
    }

    private fun connectedOnWifi(linkSpeed: Int = 866) {
        wifi.value = WifiConnectionInfo.Connected(ssid = "Home", rssi = -50, linkSpeedMbps = linkSpeed, frequencyMhz = 5180)
    }

    // ---- coverage / findings ------------------------------------------------------------------

    @Test
    fun `no router means no coverage and no findings`() = runTest(dispatcher) {
        seedOpenPlan()
        val vm = newViewModel()

        assertTrue(
            vm.state.value.coverage
                .isEmpty(),
        )
        assertTrue(
            vm.state.value.findings
                .isEmpty(),
        )
        assertNull(vm.state.value.worstDevice)
    }

    @Test
    fun `worst device is the one farthest from the router in an open plan`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = Vec2(0, 0)
        devices.value = listOf(
            DevicePin(Vec2(2, 0), "Near"),
            DevicePin(Vec2(9, 9), "Far"),
        )
        val vm = newViewModel()

        val state = vm.awaitState { it.worstDevice != null }

        assertEquals("Far", state.worstDevice!!.first.name)
    }

    @Test
    fun `a device more than 7 tiles away produces a far-from-router finding`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = Vec2(0, 0)
        devices.value = listOf(DevicePin(Vec2(9, 0), "Laptop"))
        val vm = newViewModel()

        val state = vm.awaitState { it.findings.isNotEmpty() }

        assertTrue(state.findings.any { it.description == "Laptop is far from the router." })
    }

    @Test
    fun `a device right next to the router produces no finding`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = Vec2(0, 0)
        devices.value = listOf(DevicePin(Vec2(1, 0), "Phone"))
        val vm = newViewModel()

        val state = vm.awaitState { it.coverage.isNotEmpty() && !it.isComputingCoverage }

        assertTrue(state.findings.none { it.description.startsWith("Phone") })
    }

    // ---- best-spot results go stale ------------------------------------------------------------

    @Test
    fun `moving the router discards the finished best-spot result`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = Vec2(0, 0)
        devices.value = listOf(DevicePin(Vec2(9, 9), "Far"))
        val vm = newViewModel()

        vm.onAction(DiagnoseAction.RunOptimizer)
        val done = settle(vm) { it.optimizerState is OptimizerState.Complete }
        val target = done.bestTile!!

        vm.onAction(DiagnoseAction.MoveRouter(target))
        val after = settle(vm) { it.routerPos == target }

        assertEquals(OptimizerState.Idle, after.optimizerState)
        assertNull(after.bestTile)
        assertNull(after.bestTileGainDb)
        assertTrue(after.tileScores.isEmpty())
    }

    @Test
    fun `settings changes alone keep the best-spot result`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = Vec2(0, 0)
        devices.value = listOf(DevicePin(Vec2(9, 9), "Far"))
        val vm = newViewModel()
        vm.onAction(DiagnoseAction.RunOptimizer)
        settle(vm) { it.optimizerState is OptimizerState.Complete }

        vm.onAction(DiagnoseAction.TabSpeed)
        vm.onAction(DiagnoseAction.TabBestSpot)

        assertTrue(vm.state.value.optimizerState is OptimizerState.Complete)
    }

    // ---- error handling -----------------------------------------------------------------------

    @Test
    fun `moving the router survives a database failure and reports it`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = Vec2(0, 0)
        val vm = newViewModel()
        repository.moveFailure = IllegalStateException("database or disk is full")

        vm.onAction(DiagnoseAction.MoveRouter(Vec2(3, 3)))
        advanceUntilIdle()

        assertEquals("database or disk is full", vm.state.value.errorMessage)
        vm.onAction(DiagnoseAction.DismissError)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `speed test that throws ends as Failed instead of crashing`() = runTest(dispatcher) {
        connectedOnWifi()
        speedUpdates = { flow { throw IllegalStateException("boom") } }
        val vm = newViewModel()

        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        val test = vm.state.value.speedTest
        assertTrue(test is SpeedTestState.Failed)
        assertTrue((test as SpeedTestState.Failed).reason.contains("boom"))
    }

    // ---- speed test ---------------------------------------------------------------------------

    @Test
    fun `speed test refuses to run when not on Wi-Fi`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        assertTrue(vm.state.value.speedTest is SpeedTestState.Failed)
    }

    @Test
    fun `speed test goes through running to finished`() = runTest(dispatcher) {
        connectedOnWifi()
        speedUpdates = {
            flowOf(
                SpeedTestUpdate.Running(mbps = 40f, fraction = 0.5f),
                SpeedTestUpdate.Finished(mbps = 95.5f),
            )
        }
        val vm = newViewModel()

        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        assertEquals(SpeedTestState.Finished(95.5f), vm.state.value.speedTest)
        assertNull("no comparison on the very first run", vm.state.value.previousSpeedMbps)
    }

    @Test
    fun `second run keeps the first result as the previous one`() = runTest(dispatcher) {
        connectedOnWifi()
        val vm = newViewModel()

        speedUpdates = { flowOf(SpeedTestUpdate.Finished(100f)) }
        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        speedUpdates = { flowOf(SpeedTestUpdate.Finished(150f)) }
        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        assertEquals(SpeedTestState.Finished(150f), vm.state.value.speedTest)
        assertEquals(100f, vm.state.value.previousSpeedMbps)
    }

    @Test
    fun `a failed run in between does not lose the last good result`() = runTest(dispatcher) {
        connectedOnWifi()
        val vm = newViewModel()

        speedUpdates = { flowOf(SpeedTestUpdate.Finished(100f)) }
        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        speedUpdates = { flowOf(SpeedTestUpdate.Failed("offline")) }
        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()
        assertEquals(SpeedTestState.Failed("offline"), vm.state.value.speedTest)

        speedUpdates = { flowOf(SpeedTestUpdate.Finished(120f)) }
        vm.onAction(DiagnoseAction.RunSpeedTest)
        advanceUntilIdle()

        assertEquals(100f, vm.state.value.previousSpeedMbps)
    }

    @Test
    fun `unknown link speed of minus one is shown as null`() = runTest(dispatcher) {
        connectedOnWifi(linkSpeed = -1)
        val vm = newViewModel()

        assertTrue(vm.state.value.isOnWifi)
        assertNull(vm.state.value.linkSpeedMbps)
    }

    @Test
    fun `link speed is exposed and cleared when Wi-Fi disconnects`() = runTest(dispatcher) {
        connectedOnWifi(linkSpeed = 866)
        val vm = newViewModel()
        assertEquals(866, vm.state.value.linkSpeedMbps)

        wifi.value = WifiConnectionInfo.Disconnected
        advanceUntilIdle()

        assertEquals(null, vm.state.value.linkSpeedMbps)
        assertEquals(false, vm.state.value.isOnWifi)
    }

    @Test
    fun `speed tab is reachable without a floor plan`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.onAction(DiagnoseAction.TabSpeed)

        assertEquals(DiagnoseTab.Speed, vm.state.value.tab)
    }
}

/** In-memory [DiagnoseRepository]: tests set the world directly instead of going through Room. */
private class FakeDiagnoseRepository : DiagnoseRepository {
    val context = MutableStateFlow(PlanContext(plan = null, routerPos = null, devicePins = emptyList(), roomNames = emptyMap()))
    var moveFailure: Throwable? = null

    override fun observePlanContext(): Flow<PlanContext> = context

    override suspend fun moveRouter(pos: Vec2) {
        moveFailure?.let { throw it }
        context.value = context.value.copy(routerPos = pos)
    }
}

private class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())

    override suspend fun setTheme(theme: ThemeMode) = Unit

    override suspend fun setDynamicColor(enabled: Boolean) = Unit

    override suspend fun setHapticsEnabled(enabled: Boolean) = Unit

    override suspend fun setHapticPaint(enabled: Boolean) = Unit

    override suspend fun setHapticConfirm(enabled: Boolean) = Unit

    override suspend fun setHapticError(enabled: Boolean) = Unit

    override suspend fun setAutoScanEnabled(enabled: Boolean) = Unit

    override suspend fun setPathLossExponent(value: Float) = Unit

    override suspend fun setReferenceRssiAt1m(value: Float) = Unit

    override suspend fun resetPredictionModel() = Unit
}

private class FakeConnectionRepository(private val connection: Flow<WifiConnectionInfo>) : WifiConnectionRepository {
    override fun observe(): Flow<WifiConnectionInfo> = connection
}

private class FakeSpeedTestRepository(private val updates: () -> Flow<SpeedTestUpdate>) : SpeedTestRepository {
    override fun run(): Flow<SpeedTestUpdate> = updates()
}
