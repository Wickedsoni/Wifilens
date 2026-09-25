package com.wickedcoder.wifilens

import android.content.Context
import androidx.room.Room as RoomDb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.database.WifiLensDatabase
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.feature.map.data.MapRepositoryImpl
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import com.wickedcoder.wifilens.feature.map.domain.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
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

/** Real Room (in-memory) behind the real [MapRepositoryImpl] — the layer the Map and Diagnose tabs share. */
@RunWith(AndroidJUnit4::class)
class MapRepositoryIntegrationTest {

    private lateinit var db: WifiLensDatabase
    private lateinit var repo: MapRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = RoomDb.inMemoryDatabaseBuilder(context, WifiLensDatabase::class.java).build()
        repo = MapRepositoryImpl(db.gridPlanDao(), db.roomDao(), db.pinDao(), TransactionRunner(db))
    }

    @After
    fun tearDown() = db.close()

    private fun plan(size: Int = 4, fill: CellType = CellType.Empty(Material.Drywall)) =
        GridPlan(size, size, List(size * size) { fill })

    @Test
    fun savedPlanAndRoomsAreReadBack() = runBlocking {
        val rooms = listOf(Room(1, "Living room"), Room(2, "Kitchen"))
        repo.savePlan(plan().withCell(1, 1, CellType.Floor(2)), rooms)

        assertEquals(CellType.Floor(2), repo.getActivePlan().first()!!.cellAt(1, 1))
        assertEquals(rooms, repo.getRooms().first().sortedBy { it.id })
    }

    @Test
    fun savingAgainDoesNotDuplicateRooms() = runBlocking {
        val rooms = listOf(Room(1, "Living room"))
        repeat(3) { repo.savePlan(plan(), rooms) }

        assertEquals(1, repo.getRooms().first().size)
    }

    /** Regression: an autosave used to REPLACE the plan row and cascade-delete every pin. */
    @Test
    fun savingAgainKeepsRouterAndDevicePins() = runBlocking {
        repo.savePlan(plan(), listOf(Room(1, "Living room")))
        repo.setRouterPin(Vec2(1, 1), band = "5")
        repo.addDevicePin(Vec2(2, 2), "Laptop")

        repo.savePlan(plan().withCell(0, 0, CellType.Floor(1)), listOf(Room(1, "Living room")))

        assertEquals(Vec2(1, 1), repo.getRouterPin().first())
        assertEquals(listOf("Laptop"), repo.getDevicePins().first().map { it.name })
    }

    /** Regression for the "map stuck on Living Room" bug: observers must never see new cells + old rooms. */
    @Test
    fun saveIsAtomicForObservers() = runBlocking {
        repo.savePlan(plan(), listOf(Room(1, "Living room")))

        val torn = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.IO)
        val job = repo.observePlan()
            .onEach { (p, r) ->
                val newCells = p?.cellAt(0, 0) is CellType.Floor
                if (newCells && r.size == 1) torn += "new cells with old rooms"
            }
            .launchIn(scope)
        delay(200)

        repo.savePlan(
            plan().withCell(0, 0, CellType.Floor(1)),
            listOf(Room(1, "Living room"), Room(2, "Kitchen")),
        )
        delay(500)
        job.cancel()

        assertTrue("observers saw a half-saved state: $torn", torn.isEmpty())
    }

    @Test
    fun clearPlanRemovesRoomsAndPins() = runBlocking {
        repo.savePlan(plan(), listOf(Room(1, "Living room")))
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
        repo.savePlan(plan(), listOf(Room(1, "Living room")))

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
        repo.savePlan(GridPlan(width, 1, cells), emptyList())

        val loaded = repo.getActivePlan().first()!!

        assertEquals(cells, loaded.cells.toList())
        assertFalse(loaded.cells.isEmpty())
    }
}
