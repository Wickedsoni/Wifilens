package com.wickedcoder.wifilens.feature.map.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.ThemeMode
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import com.wickedcoder.wifilens.feature.map.domain.PlanSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeMapRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeMapRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(handle: SavedStateHandle = SavedStateHandle()): MapViewModel {
        val vm = MapViewModel(repo, handle, FakeSettingsRepository())
        advanceUntilIdle() // let init collectors deliver the first DB snapshot
        return vm
    }

    private fun emptyPlan(size: Int = 5) =
        GridPlan(size, size, List(size * size) { CellType.Empty(Material.Drywall) })

    // ---- loading / room selection -------------------------------------------------------------

    @Test
    fun `first room is auto-selected once rooms load`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room"), Room(2, "Kitchen")))
        val vm = newViewModel()

        assertEquals(1, vm.state.value.activeRoomId)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `creating a room selects it and switches to the room tool`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()
        vm.onAction(MapAction.SelectTool(MapTool.Wall))

        vm.onAction(MapAction.CreateRoom("Kitchen"))

        val state = vm.state.value
        assertEquals(listOf("Living room", "Kitchen"), state.rooms.map { it.name })
        assertEquals(2, state.activeRoomId)
        assertEquals(MapTool.Room, state.activeTool)
    }

    @Test
    fun `new room is persisted after the autosave debounce`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.CreateRoom("Kitchen"))
        advanceTimeBy(1_400)
        runCurrent()
        assertEquals("not saved before the debounce elapses", 0, repo.saveCount)

        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, repo.saveCount)
        assertEquals(listOf("Living room", "Kitchen"), repo.savedRooms.map { it.name })
    }

    /** Regression: the Map tab used to snap back to the first room after adding a new one. */
    @Test
    fun `stale database emission does not revert a room created while a save was in flight`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()
        repo.saveGate = CompletableDeferred() // hold the first save open

        vm.onAction(MapAction.CreateRoom("Kitchen"))
        advanceTimeBy(1_600) // debounce fires; savePlan([Living, Kitchen]) now suspended on the gate
        runCurrent()

        vm.onAction(MapAction.CreateRoom("Bedroom")) // edit made mid-save

        repo.saveGate!!.complete(Unit) // first save commits, DB re-emits [Living, Kitchen] (no Bedroom)
        advanceUntilIdle()

        assertEquals(
            listOf("Living room", "Kitchen", "Bedroom"),
            vm.state.value.rooms
                .map { it.name },
        )
        assertEquals("active room must stay on the newest room", 3, vm.state.value.activeRoomId)
    }

    @Test
    fun `active room falls back to first room when the active one disappears`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room"), Room(2, "Kitchen")))
        val vm = newViewModel()
        vm.onAction(MapAction.SelectRoom(2))

        repo.rooms.value = listOf(Room(1, "Living room"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.activeRoomId)
    }

    // ---- room management (rename / delete / validation) ---------------------------------------

    @Test
    fun `blank or duplicate room names are rejected with an error`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.CreateRoom("   "))
        vm.onAction(MapAction.CreateRoom("living ROOM"))

        assertEquals(
            listOf("Living room"),
            vm.state.value.rooms
                .map { it.name },
        )
        vm.events.test {
            assertEquals(MapEvent.ShowError("Enter a room name"), awaitItem())
            assertEquals(MapEvent.ShowError("A room called \"living ROOM\" already exists"), awaitItem())
        }
    }

    @Test
    fun `room names are trimmed`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.CreateRoom("  Kitchen "))

        assertEquals(
            "Kitchen",
            vm.state.value.rooms
                .last()
                .name,
        )
    }

    @Test
    fun `renaming a room updates it and is persisted`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room"), Room(2, "Kitchen")))
        val vm = newViewModel()

        vm.onAction(MapAction.RenameRoom(2, "Galley"))
        advanceTimeBy(1_600)
        runCurrent()

        assertEquals(
            listOf("Living room", "Galley"),
            vm.state.value.rooms
                .map { it.name },
        )
        assertEquals(listOf("Living room", "Galley"), repo.savedRooms.map { it.name })
    }

    @Test
    fun `renaming to another rooms name is rejected but keeping your own name is fine`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room"), Room(2, "Kitchen")))
        val vm = newViewModel()

        vm.onAction(MapAction.RenameRoom(2, "Living room"))
        vm.onAction(MapAction.RenameRoom(2, "Kitchen"))

        assertEquals(
            listOf("Living room", "Kitchen"),
            vm.state.value.rooms
                .map { it.name },
        )
        vm.events.test {
            assertEquals(MapEvent.ShowError("A room called \"Living room\" already exists"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `deleting a room unassigns its tiles and selects another room`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room"), Room(2, "Kitchen")))
        val vm = newViewModel()
        vm.onAction(MapAction.SelectRoom(2))
        vm.onAction(MapAction.PaintCell(0, 0)) // Kitchen floor
        assertTrue(vm.state.value.canUndo)

        vm.onAction(MapAction.DeleteRoom(2))

        val state = vm.state.value
        assertEquals(listOf(1), state.rooms.map { it.id })
        assertEquals(1, state.activeRoomId)
        assertEquals(CellType.Floor(UNASSIGNED_ROOM_ID), state.plan!!.cellAt(0, 0))
        assertFalse("undo history would reference the deleted room", state.canUndo)
    }

    @Test
    fun `device pin names are trimmed and capped`() = runTest(dispatcher) {
        repo.seed(GridPlan(5, 5, List(25) { CellType.Floor(1) }), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.PlaceDevice(1, 1, "  " + "x".repeat(80)))
        advanceUntilIdle()

        assertEquals(
            MAX_NAME_LENGTH,
            repo.devices.value
                .single()
                .name.length,
        )
    }

    // ---- painting / undo / redo ---------------------------------------------------------------

    @Test
    fun `painting with the room tool writes the active room id`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room"), Room(2, "Kitchen")))
        val vm = newViewModel()
        vm.onAction(MapAction.SelectRoom(2))

        vm.onAction(MapAction.PaintCell(1, 1))

        assertEquals(
            CellType.Floor(roomId = 2),
            vm.state.value.plan!!
                .cellAt(1, 1),
        )
    }

    @Test
    fun `out of bounds paint is ignored`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.PaintCell(-1, 0))
        vm.onAction(MapAction.PaintCell(5, 0))
        vm.onAction(MapAction.PaintCell(0, 5))

        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `repainting the same cell type is a no-op and not undoable`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.PaintCell(0, 0))
        vm.onAction(MapAction.PaintCell(0, 0))
        vm.onAction(MapAction.Undo)

        assertFalse("second identical paint must not add an undo step", vm.state.value.canUndo)
        assertEquals(
            CellType.Empty(Material.Drywall),
            vm.state.value.plan!!
                .cellAt(0, 0),
        )
    }

    @Test
    fun `undo then redo restores the painted cell and a new paint clears redo`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.PaintCell(0, 0))
        vm.onAction(MapAction.Undo)
        assertEquals(
            CellType.Empty(Material.Drywall),
            vm.state.value.plan!!
                .cellAt(0, 0),
        )
        assertTrue(vm.state.value.canRedo)

        vm.onAction(MapAction.Redo)
        assertEquals(
            CellType.Floor(1),
            vm.state.value.plan!!
                .cellAt(0, 0),
        )

        vm.onAction(MapAction.Undo)
        vm.onAction(MapAction.PaintCell(1, 0))
        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `undo history is capped at 20 steps`() = runTest(dispatcher) {
        repo.seed(emptyPlan(size = 10), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        repeat(25) { vm.onAction(MapAction.PaintCell(it % 10, it / 10)) }
        var undone = 0
        while (vm.state.value.canUndo) {
            vm.onAction(MapAction.Undo)
            undone++
        }

        assertEquals(20, undone)
    }

    // ---- persistence / errors -----------------------------------------------------------------

    @Test
    fun `persistIfDirty flushes immediately without waiting for the debounce`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.PaintCell(2, 2))
        vm.persistIfDirty()
        runCurrent()

        assertEquals(1, repo.saveCount)
    }

    @Test
    fun `persistIfDirty does nothing when there are no local edits`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.persistIfDirty()
        runCurrent()

        assertEquals(0, repo.saveCount)
    }

    @Test
    fun `save failure is reported as a ShowError event`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()
        repo.failNextSave = MapRepositoryException("disk full")

        vm.onAction(MapAction.PaintCell(0, 0))
        vm.persistIfDirty()
        runCurrent()

        vm.events.test { assertEquals(MapEvent.ShowError("disk full"), awaitItem()) }
    }

    /** A raw SQLite failure (disk full, locked DB) must surface as an error, not crash the app or kill autosave. */
    @Test
    fun `unexpected save exception is reported and autosave keeps working afterwards`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()
        repo.failNextSave = IllegalStateException("database or disk is full")

        vm.onAction(MapAction.PaintCell(0, 0))
        advanceTimeBy(1_600) // debounced autosave hits the failure
        runCurrent()
        vm.events.test { assertEquals(MapEvent.ShowError("database or disk is full"), awaitItem()) }

        vm.onAction(MapAction.PaintCell(1, 0))
        advanceTimeBy(1_600)
        runCurrent()
        assertEquals("autosave must survive an earlier failure", 1, repo.saveCount)
    }

    @Test
    fun `clearing the plan resets everything`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()
        vm.onAction(MapAction.PaintCell(0, 0))

        vm.onAction(MapAction.ClearPlan)
        advanceUntilIdle()

        assertNull(vm.state.value.plan)
        assertTrue(
            vm.state.value.rooms
                .isEmpty(),
        )
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `router can only be placed on a walkable tile`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val vm = newViewModel()

        vm.onAction(MapAction.PlaceRouter(0, 0)) // still an Empty (wall) tile
        advanceUntilIdle()
        assertNull(repo.router.value)

        vm.onAction(MapAction.PaintCell(0, 0))
        vm.onAction(MapAction.PlaceRouter(0, 0))
        advanceUntilIdle()
        assertNotNull(repo.router.value)
    }

    @Test
    fun `tool selection survives process death through SavedStateHandle`() = runTest(dispatcher) {
        repo.seed(emptyPlan(), listOf(Room(1, "Living room")))
        val handle = SavedStateHandle()
        newViewModel(handle).onAction(MapAction.SelectTool(MapTool.Door))

        val restored = newViewModel(handle)

        assertEquals(MapTool.Door, restored.state.value.activeTool)
    }
}

private class FakeMapRepository : MapRepository {
    val plan = MutableStateFlow<GridPlan?>(null)
    val rooms = MutableStateFlow<List<Room>>(emptyList())
    val router = MutableStateFlow<Vec2?>(null)
    val devices = MutableStateFlow<List<DevicePin>>(emptyList())

    var saveCount = 0
    var savedRooms: List<Room> = emptyList()
    var saveGate: CompletableDeferred<Unit>? = null
    var failNextSave: Throwable? = null

    fun seed(plan: GridPlan, rooms: List<Room>) {
        this.plan.value = plan
        this.rooms.value = rooms
    }

    override fun getActivePlan(): Flow<GridPlan?> = plan

    override fun observePlan(): Flow<PlanSnapshot> = combine(plan, rooms) { p, r -> PlanSnapshot(p, r) }

    override fun getRooms(): Flow<List<Room>> = rooms

    override fun getRouterPin(): Flow<Vec2?> = router

    override fun getDevicePins(): Flow<List<DevicePin>> = devices

    override suspend fun savePlan(plan: GridPlan, rooms: List<Room>) {
        failNextSave?.let {
            failNextSave = null
            throw it
        }
        saveGate?.await()
        saveCount++
        savedRooms = rooms
        // A real DB re-emits what was persisted, which is what can clobber newer in-memory edits.
        this.plan.value = plan
        this.rooms.value = rooms
    }

    override suspend fun clearPlan() {
        plan.value = null
        rooms.value = emptyList()
    }

    override suspend fun setRouterPin(pos: Vec2, band: String) {
        router.value = pos
    }

    override suspend fun addDevicePin(pos: Vec2, name: String) {
        devices.value = devices.value + DevicePin(pos, name)
    }

    override suspend fun removeDevicePin(pos: Vec2) {
        devices.value = devices.value.filterNot { it.pos == pos }
    }
}

private class FakeSettingsRepository : SettingsRepository {
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
