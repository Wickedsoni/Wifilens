package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnoseUseCasesTest {
    private val settings = AppSettings()

    private fun openPlan(size: Int = 10) = GridPlan(size, size, List(size * size) { CellType.Floor(1) })

    /** A wall column at x = [wallX] splitting an otherwise open plan. */
    private fun planWithWall(size: Int = 10, wallX: Int = 5): GridPlan =
        GridPlan(
            size,
            size,
            List(size * size) { i -> if (i % size == wallX) CellType.Empty(Material.Concrete) else CellType.Floor(1) },
        )

    private fun context(
        plan: GridPlan? = openPlan(),
        router: Vec2? = Vec2(0, 0),
        devices: List<DevicePin> = emptyList(),
        roomNames: Map<Int, String> = mapOf(1 to "Living room"),
    ) = PlanContext(plan, router, devices, roomNames)

    // ---- AnalyzeCoverage ------------------------------------------------------------------------

    @Test
    fun `no plan or no router gives an empty report`() {
        val analyze = AnalyzeCoverage()

        assertEquals(CoverageReport.Empty, analyze(context(plan = null), settings))
        assertEquals(CoverageReport.Empty, analyze(context(router = null), settings))
    }

    @Test
    fun `every floor tile is covered and walls are skipped`() {
        val report = AnalyzeCoverage()(context(plan = planWithWall()), settings)

        assertEquals(90, report.coverage.size) // 100 tiles minus the 10-tile wall column
        assertTrue(report.coverage.none { it.pos.x == 5 })
    }

    @Test
    fun `signal gets weaker with distance`() {
        val report = AnalyzeCoverage()(context(), settings)
        val byPos = report.coverage.associate { it.pos to it.rssi }

        assertTrue(byPos.getValue(Vec2(2, 0)) > byPos.getValue(Vec2(9, 9)))
    }

    @Test
    fun `worst device is the farthest one`() {
        val near = DevicePin(Vec2(2, 0), "Near")
        val far = DevicePin(Vec2(9, 9), "Far")

        val report = AnalyzeCoverage()(context(devices = listOf(near, far)), settings)

        assertEquals(far, report.worstDevice?.first)
    }

    @Test
    fun `no devices means no worst device`() {
        assertNull(AnalyzeCoverage()(context(), settings).worstDevice)
    }

    @Test
    fun `rooms are summarised by name with a fallback for unnamed ones`() {
        val plan = GridPlan(4, 4, List(16) { i -> CellType.Floor(if (i % 4 < 2) 1 else 2) })

        val report = AnalyzeCoverage()(context(plan = plan, roomNames = mapOf(1 to "Kitchen")), settings)

        assertEquals(listOf("Kitchen", "Room 2"), report.roomSummaries.map { it.name })
        assertEquals(listOf(1, 2), report.roomSummaries.map { it.roomId })
    }

    @Test
    fun `a device far from the router is flagged as far`() {
        val report = AnalyzeCoverage()(context(devices = listOf(DevicePin(Vec2(9, 9), "TV"))), settings)

        assertTrue(report.findings.any { it.severity == Severity.Fair && it.description == "TV is far from the router." })
    }

    @Test
    fun `a device behind walls with weak signal is a poor finding`() {
        val plan = GridPlan(
            30,
            3,
            List(90) { i -> if (i % 30 in setOf(10, 11, 12)) CellType.Empty(Material.Concrete) else CellType.Floor(1) },
        )

        val report = AnalyzeCoverage()(
            context(plan = plan, router = Vec2(0, 1), devices = listOf(DevicePin(Vec2(29, 1), "Console"))),
            settings,
        )

        val finding = report.findings.singleOrNull { it.description.startsWith("Console") }
        assertNotNull(finding)
        assertEquals(Severity.Poor, finding.severity)
    }

    @Test
    fun `a nearby device produces no finding`() {
        val report = AnalyzeCoverage()(context(devices = listOf(DevicePin(Vec2(2, 0), "Phone"))), settings)

        assertTrue(report.findings.none { it.description.startsWith("Phone") })
    }

    // ---- FindBestRouterSpot ---------------------------------------------------------------------

    @Test
    fun `nothing to optimise without devices`() {
        assertNull(FindBestRouterSpot()(openPlan(), emptyList(), Vec2(0, 0), settings))
    }

    @Test
    fun `every walkable tile gets a score and walls do not`() {
        val plan = planWithWall()

        val result = FindBestRouterSpot()(plan, listOf(DevicePin(Vec2(9, 9), "Far")), Vec2(0, 0), settings)

        assertNotNull(result)
        assertEquals(90, result.scores.size)
        assertTrue(result.scores.keys.none { it.x == 5 })
    }

    @Test
    fun `best spot is next to the only device`() {
        val device = DevicePin(Vec2(9, 9), "Far")

        val result = FindBestRouterSpot()(openPlan(), listOf(device), Vec2(0, 0), settings)

        assertNotNull(result)
        val best = assertNotNull(result.bestTile)
        assertTrue(best.x >= 8 && best.y >= 8, "best tile $best should be next to $device")
        assertTrue((result.gainDb ?: 0f) > 0f)
        assertEquals(false, result.alreadyOptimal)
    }

    @Test
    fun `a router already on the best tile is reported as optimal with zero gain`() {
        // Corner device: tiles within 1 m all tie at the reference signal and the first tie wins, which is the
        // top-left tile. (A router on a *later* tied tile is not called optimal yet: see docs/bug-log.md B-29.)
        val device = DevicePin(Vec2(0, 0), "Desk")

        val result = FindBestRouterSpot()(openPlan(), listOf(device), Vec2(0, 0), settings)

        assertNotNull(result)
        assertTrue(result.alreadyOptimal)
        assertEquals(0f, result.gainDb)
    }

    @Test
    fun `without a router there is a best tile but no gain figure`() {
        val result = FindBestRouterSpot()(openPlan(), listOf(DevicePin(Vec2(9, 9), "Far")), null, settings)

        assertNotNull(result)
        assertNotNull(result.bestTile)
        assertNull(result.gainDb)
    }

    @Test
    fun `progress is reported and finishes at 100 percent`() {
        val progress = mutableListOf<Float>()

        FindBestRouterSpot()(openPlan(), listOf(DevicePin(Vec2(9, 9), "Far")), Vec2(0, 0), settings) { progress += it }

        assertTrue(progress.isNotEmpty())
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress, "progress never goes backwards")
    }
}
