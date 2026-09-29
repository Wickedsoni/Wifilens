package com.wickedcoder.wifilens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.database.WifiLensDatabase
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.data.MapRepositoryImpl
import com.wickedcoder.wifilens.feature.map.data.PlanContentStore
import com.wickedcoder.wifilens.feature.map.data.PlanDocumentStore
import com.wickedcoder.wifilens.feature.map.data.PlanRepositoryImpl
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room as RoomDb

/** Real Room (in-memory) behind the real [MapRepositoryImpl] — the layer the Map and Diagnose tabs share. */
@RunWith(AndroidJUnit4::class)
class MapRepositoryIntegrationTest {
    private lateinit var db: WifiLensDatabase
    private lateinit var repo: MapRepositoryImpl
    private lateinit var plans: PlanRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = RoomDb.inMemoryDatabaseBuilder(context, WifiLensDatabase::class.java).build()
        val clock = Clock(System::currentTimeMillis)
        val transactions = TransactionRunner(db)
        val content = PlanContentStore(db.gridPlanDao(), db.planDao(), db.roomDao(), db.pinDao(), clock)
        repo = MapRepositoryImpl(db.gridPlanDao(), db.planDao(), db.roomDao(), db.pinDao(), content, transactions, clock)
        plans = PlanRepositoryImpl(db.planDao(), content, transactions, PlanDocumentStore(context, Dispatchers.IO), clock)
    }

    @After
    fun tearDown() = db.close()

    /** Creates the plan on first use, then saves by id (v2 saves are addressed to a plan). */
    private var planId: Long? = null

    private suspend fun save(plan: GridPlan, rooms: List<Room>) {
        val id = planId ?: plans.createPlan("Home", plan).also { planId = it }
        repo.savePlan(id, plan, rooms)
    }

    private fun plan(size: Int = 4, fill: CellType = CellType.Empty(Material.Drywall)) =
        GridPlan(size, size, List(size * size) { fill })

    @Test
    fun savedPlanAndRoomsAreReadBack() = runBlocking {
        val rooms = listOf(Room(1, "Living room"), Room(2, "Kitchen"))
        save(plan().withCell(1, 1, CellType.Floor(2)), rooms)

        assertEquals(CellType.Floor(2), repo.getActivePlan().first()!!.cellAt(1, 1))
        assertEquals(rooms, repo.getRooms().first().sortedBy { it.id })
    }

    @Test
    fun savingAgainDoesNotDuplicateRooms() = runBlocking {
        val rooms = listOf(Room(1, "Living room"))
        repeat(3) { save(plan(), rooms) }

        assertEquals(1, repo.getRooms().first().size)
    }

    /** Regression: an autosave used to REPLACE the plan row and cascade-delete every pin. */
    @Test
    fun savingAgainKeepsRouterAndDevicePins() = runBlocking {
        save(plan(), listOf(Room(1, "Living room")))
        repo.setRouterPin(Vec2(1, 1), band = "5")
        repo.addDevicePin(Vec2(2, 2), "Laptop")

        save(plan().withCell(0, 0, CellType.Floor(1)), listOf(Room(1, "Living room")))

        assertEquals(Vec2(1, 1), repo.getRouterPin().first())
        assertEquals(listOf("Laptop"), repo.getDevicePins().first().map { it.name })
    }

    /** Regression for the "map stuck on Living Room" bug: observers must never see new cells + old rooms. */
    @Test
    fun saveIsAtomicForObservers() = runBlocking {
        save(plan(), listOf(Room(1, "Living room")))

        val torn = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.IO)
        val job = repo
            .observePlan()
            .onEach { snapshot ->
                val newCells = snapshot.plan?.cellAt(0, 0) is CellType.Floor
                if (newCells && snapshot.rooms.size == 1) torn += "new cells with old rooms"
            }.launchIn(scope)
        delay(200)

        save(
            plan().withCell(0, 0, CellType.Floor(1)),
            listOf(Room(1, "Living room"), Room(2, "Kitchen")),
        )
        delay(500)
        job.cancel()

        assertTrue("observers saw a half-saved state: $torn", torn.isEmpty())
    }

    @Test
    fun clearPlanRemovesRoomsAndPins() = runBlocking {
        save(plan(), listOf(Room(1, "Living room")))
        repo.setRouterPin(Vec2(0, 0), band = "5")
        repo.addDevicePin(Vec2(1, 1), "Phone")

        repo.clearPlan()

        assertNull(repo.getActivePlan().first())
        assertTrue(repo.getRooms().first().isEmpty())
        assertNull(repo.getRouterPin().first())
        assertTrue(repo.getDevicePins().first().isEmpty())
    }

    @Test
    fun movingTheRouterReplacesRatherThanAdds() = runBlocking {
        save(plan(), listOf(Room(1, "Living room")))

        repo.setRouterPin(Vec2(0, 0), band = "5")
        repo.setRouterPin(Vec2(3, 3), band = "5")

        assertEquals(Vec2(3, 3), repo.getRouterPin().first())
    }

    @Test
    fun placingAPinWithoutAPlanFailsWithADomainError() = runBlocking {
        try {
            repo.setRouterPin(Vec2(0, 0), band = "5")
            fail("expected MapRepositoryException")
        } catch (e: MapRepositoryException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun everyCellTypeSurvivesTheDatabaseRoundTrip() = runBlocking {
        val materials = listOf(Material.Drywall, Material.Wood, Material.Glass, Material.Brick, Material.Concrete, Material.Metal)
        val cells = buildList<CellType> {
            materials.forEach { add(CellType.Empty(it)) }
            add(CellType.Door)
            add(CellType.Floor(0))
            add(CellType.Floor(7))
            add(CellType.Floor(120))
        }
        val width = cells.size
        save(GridPlan(width, 1, cells), emptyList())

        val loaded = repo.getActivePlan().first()!!

        assertEquals(cells, loaded.cells.toList())
        assertFalse(loaded.cells.isEmpty())
    }

    // ---- multiple plans (Sprint 6) -----------------------------------------------------------------

    @Test
    fun creatingASecondPlanMakesItActiveAndKeepsTheFirst() = runBlocking {
        val first = plans.createPlan("Home", plan())
        Thread.sleep(5) // distinct lastOpenedAt
        val second = plans.createPlan("Office", plan(size = 6))

        val plans = plans.observePlans().first()
        assertEquals(listOf(second, first), plans.map { it.id })
        assertEquals(listOf(true, false), plans.map { it.isActive })
        assertEquals("Office", repo.observePlan().first().name)
    }

    @Test
    fun deletingTheActivePlanActivatesTheMostRecentOther() = runBlocking {
        val home = plans.createPlan("Home", plan())
        Thread.sleep(5)
        val office = plans.createPlan("Office", plan())

        plans.deletePlan(office)

        assertEquals(home, repo.observePlan().first().planId)
        assertEquals(1, plans.observePlans().first().size)
    }

    @Test
    fun duplicateCopiesRoomsAndPinsIntoANewActivePlan() = runBlocking {
        val source = plans.createPlan("Home", plan(fill = CellType.Floor(1)))
        repo.savePlan(source, plan(fill = CellType.Floor(1)), listOf(Room(1, "Living room")))
        repo.setRouterPin(Vec2(0, 0), band = "5")
        repo.addDevicePin(Vec2(1, 1), "TV")

        val copy = plans.duplicatePlan(source, "Home copy")

        val snapshot = repo.observePlan().first()
        assertEquals(copy, snapshot.planId)
        assertEquals("Home copy", snapshot.name)
        assertEquals(listOf(Room(1, "Living room")), snapshot.rooms)
        assertEquals(Vec2(0, 0), repo.getRouterPin().first())
        assertEquals(listOf("TV"), repo.getDevicePins().first().map { it.name })
    }

    @Test
    fun savingByIdNeverTouchesAnotherPlan() = runBlocking {
        val home = plans.createPlan("Home", plan())
        Thread.sleep(5)
        plans.createPlan("Office", plan())

        repo.savePlan(home, plan().withCell(0, 0, CellType.Door), emptyList())

        val office = repo.observePlan().first()
        assertEquals("Office", office.name)
        assertEquals(CellType.Empty(Material.Drywall), office.plan?.cellAt(0, 0))
        plans.openPlan(home)
        assertEquals(
            CellType.Door,
            repo
                .observePlan()
                .first()
                .plan
                ?.cellAt(0, 0),
        )
    }
}
