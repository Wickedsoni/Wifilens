package com.wickedcoder.wifilens.feature.diagnose.presentation

import com.wickedcoder.wifilens.core.database.AppSettings
import com.wickedcoder.wifilens.core.database.CellEntity
import com.wickedcoder.wifilens.core.database.DevicePinEntity
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanEntity
import com.wickedcoder.wifilens.core.database.GridPlanSnapshot
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import com.wickedcoder.wifilens.core.database.PinDao
import com.wickedcoder.wifilens.core.database.RoomDao
import com.wickedcoder.wifilens.core.database.RoomEntity
import com.wickedcoder.wifilens.core.database.RouterPinEntity
import com.wickedcoder.wifilens.core.database.SettingsRepository
import com.wickedcoder.wifilens.core.database.ThemeMode
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.wifi.SpeedTestUpdate
import com.wickedcoder.wifilens.core.wifi.WifiConnectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import com.wickedcoder.wifilens.core.rf.Vec2
import kotlinx.coroutines.flow.first
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

    private val dispatcher = StandardTestDispatcher()

    private val plan = MutableStateFlow<GridPlanWithCells?>(null)
    private val router = MutableStateFlow<RouterPinEntity?>(null)
    private val devices = MutableStateFlow<List<DevicePinEntity>>(emptyList())
    private val rooms = MutableStateFlow<List<RoomEntity>>(emptyList())
    private val wifi = MutableStateFlow<WifiConnectionInfo>(WifiConnectionInfo.Disconnected)
    private var failRouterInsert: Throwable? = null
    private var speedUpdates: () -> Flow<SpeedTestUpdate> = { flowOf(SpeedTestUpdate.Finished(100f)) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): DiagnoseViewModel {
        val vm = DiagnoseViewModel(
            gridPlanDao = FakeGridPlanDao(plan),
            pinDao = FakePinDao(router, devices) { failRouterInsert },
            roomDao = FakeRoomDao(rooms),
            settingsRepository = FakeSettings(),
            wifiConnectionFlow = { wifi },
            downloadSpeedFlow = { speedUpdates() },
        )
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
        val cells = (0 until size).flatMap { y -> (0 until size).map { x -> CellEntity(1, x, y, CellType.Floor(1)) } }
        plan.value = GridPlanWithCells(GridPlanEntity(id = 1, name = "Home", width = size, height = size), cells)
        rooms.value = listOf(RoomEntity(planId = 1, roomId = 1, name = "Living room"))
    }

    private fun connectedOnWifi(linkSpeed: Int = 866) {
        wifi.value = WifiConnectionInfo.Connected(ssid = "Home", rssi = -50, linkSpeedMbps = linkSpeed, frequencyMhz = 5180)
    }

    // ---- coverage / findings ------------------------------------------------------------------

    @Test
    fun `no router means no coverage and no findings`() = runTest(dispatcher) {
        seedOpenPlan()
        val vm = newViewModel()

        assertTrue(vm.state.value.coverage.isEmpty())
        assertTrue(vm.state.value.findings.isEmpty())
        assertNull(vm.state.value.worstDevice)
    }

    @Test
    fun `worst device is the one farthest from the router in an open plan`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "5")
        devices.value = listOf(
            DevicePinEntity(planId = 1, x = 2, y = 0, name = "Near"),
            DevicePinEntity(planId = 1, x = 9, y = 9, name = "Far"),
        )
        val vm = newViewModel()

        val state = vm.awaitState { it.worstDevice != null }

        assertEquals("Far", state.worstDevice!!.first.name)
    }

    @Test
    fun `a device more than 7 tiles away produces a far-from-router finding`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "5")
        devices.value = listOf(DevicePinEntity(planId = 1, x = 9, y = 0, name = "Laptop"))
        val vm = newViewModel()

        val state = vm.awaitState { it.findings.isNotEmpty() }

        assertTrue(state.findings.any { it.description == "Laptop is far from the router." })
    }

    @Test
    fun `a device right next to the router produces no finding`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "5")
        devices.value = listOf(DevicePinEntity(planId = 1, x = 1, y = 0, name = "Phone"))
        val vm = newViewModel()

        val state = vm.awaitState { it.coverage.isNotEmpty() && !it.isComputingCoverage }

        assertTrue(state.findings.none { it.description.startsWith("Phone") })
    }

    // ---- best-spot results go stale ------------------------------------------------------------

    @Test
    fun `moving the router discards the finished best-spot result`() = runTest(dispatcher) {
        seedOpenPlan()
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "5")
        devices.value = listOf(DevicePinEntity(planId = 1, x = 9, y = 9, name = "Far"))
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
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "5")
        devices.value = listOf(DevicePinEntity(planId = 1, x = 9, y = 9, name = "Far"))
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
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "5")
        val vm = newViewModel()
        failRouterInsert = IllegalStateException("database or disk is full")

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

private class FakeGridPlanDao(private val plan: MutableStateFlow<GridPlanWithCells?>) : GridPlanDao {
    override suspend fun insertPlan(plan: GridPlanEntity): Long = 1
    override suspend fun updatePlan(plan: GridPlanEntity) = Unit
    override suspend fun deletePlan(plan: GridPlanEntity) = Unit
    override suspend fun insertCells(cells: List<CellEntity>) = Unit
    override fun getActivePlan(): Flow<GridPlanWithCells?> = plan
    override fun observeSnapshot(): Flow<GridPlanSnapshot?> = flowOf(null)
}

private class FakePinDao(
    private val router: MutableStateFlow<RouterPinEntity?>,
    private val devices: MutableStateFlow<List<DevicePinEntity>>,
    private val routerInsertFailure: () -> Throwable? = { null },
) : PinDao {
    override suspend fun insertRouterPin(pin: RouterPinEntity): Long {
        routerInsertFailure()?.let { throw it }
        router.value = pin
        return 1
    }

    override suspend fun deleteRouterPin(pin: RouterPinEntity) {
        router.value = null
    }

    override suspend fun insertDevicePin(pin: DevicePinEntity): Long {
        devices.value = devices.value + pin
        return 1
    }

    override suspend fun deleteDevicePin(pin: DevicePinEntity) {
        devices.value = devices.value - pin
    }

    override fun observeRouterPin(planId: Long): Flow<RouterPinEntity?> = router
    override fun observeDevicePins(planId: Long): Flow<List<DevicePinEntity>> = devices
}

private class FakeRoomDao(private val rooms: MutableStateFlow<List<RoomEntity>>) : RoomDao {
    override suspend fun insertRoom(room: RoomEntity): Long = 1
    override suspend fun deleteRoom(room: RoomEntity) = Unit
    override suspend fun deleteRoomsForPlan(planId: Long) = Unit
    override fun getRoomsForPlan(planId: Long): Flow<List<RoomEntity>> = rooms
}

private class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    override suspend fun setTheme(theme: ThemeMode) = Unit
    override suspend fun setHapticsEnabled(enabled: Boolean) = Unit
    override suspend fun setHapticPaint(enabled: Boolean) = Unit
    override suspend fun setHapticConfirm(enabled: Boolean) = Unit
    override suspend fun setHapticError(enabled: Boolean) = Unit
    override suspend fun setAutoScanEnabled(enabled: Boolean) = Unit
    override suspend fun setPathLossExponent(value: Float) = Unit
    override suspend fun setReferenceRssiAt1m(value: Float) = Unit
    override suspend fun resetPredictionModel() = Unit
}
