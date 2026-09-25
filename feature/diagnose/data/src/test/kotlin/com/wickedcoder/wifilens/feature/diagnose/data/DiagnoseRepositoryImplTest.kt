package com.wickedcoder.wifilens.feature.diagnose.data

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
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.Vec2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repository against in-memory fake DAOs (the real Room path is covered by the instrumented tests). */
class DiagnoseRepositoryImplTest {
    private val plan = MutableStateFlow<GridPlanWithCells?>(null)
    private val router = MutableStateFlow<RouterPinEntity?>(null)
    private val devices = MutableStateFlow<List<DevicePinEntity>>(emptyList())
    private val rooms = MutableStateFlow<List<RoomEntity>>(emptyList())
    private var routerInsertFailure: Throwable? = null

    private val repository = DiagnoseRepositoryImpl(
        gridPlanDao = FakeGridPlanDao(plan),
        pinDao = FakePinDao(router, devices) { routerInsertFailure },
        roomDao = FakeRoomDao(rooms),
    )

    private fun seedPlan(size: Int = 4) {
        val cells = (0 until size).flatMap { y -> (0 until size).map { x -> CellEntity(1, x, y, CellType.Floor(1)) } }
        plan.value = GridPlanWithCells(GridPlanEntity(id = 1, name = "Home", width = size, height = size), cells)
    }

    @Test
    fun `no plan emits an empty context`() = runTest {
        val context = repository.observePlanContext().first()

        assertNull(context.plan)
        assertNull(context.routerPos)
        assertTrue(context.devicePins.isEmpty())
        assertTrue(context.roomNames.isEmpty())
    }

    @Test
    fun `a stored plan is mapped to domain types`() = runTest {
        seedPlan()
        router.value = RouterPinEntity(planId = 1, x = 1, y = 2, band = "5")
        devices.value = listOf(DevicePinEntity(planId = 1, x = 3, y = 3, name = "Laptop"))
        rooms.value = listOf(RoomEntity(planId = 1, roomId = 1, name = "Living room"))

        val context = repository.observePlanContext().first()

        assertEquals(4, context.plan?.width)
        assertEquals(Vec2(1, 2), context.routerPos)
        assertEquals(listOf(DevicePin(Vec2(3, 3), "Laptop")), context.devicePins)
        assertEquals(mapOf(1 to "Living room"), context.roomNames)
    }

    @Test
    fun `moving the router writes the new position and keeps its band`() = runTest {
        seedPlan()
        router.value = RouterPinEntity(planId = 1, x = 0, y = 0, band = "2.4")

        repository.moveRouter(Vec2(2, 3))

        assertEquals(RouterPinEntity(planId = 1, x = 2, y = 3, band = "2.4"), router.value)
    }

    @Test
    fun `moving a router that has no pin yet uses the default band`() = runTest {
        seedPlan()

        repository.moveRouter(Vec2(1, 1))

        assertEquals("5", router.value?.band)
    }

    @Test
    fun `moving the router without a plan does nothing`() = runTest {
        repository.moveRouter(Vec2(1, 1))

        assertNull(router.value)
    }

    @Test
    fun `a storage failure is rethrown to the caller`() = runTest {
        seedPlan()
        routerInsertFailure = IllegalStateException("disk full")

        val failure = runCatching { repository.moveRouter(Vec2(1, 1)) }.exceptionOrNull()

        assertEquals("disk full", failure?.message)
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
