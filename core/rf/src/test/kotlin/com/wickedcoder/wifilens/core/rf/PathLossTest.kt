package com.wickedcoder.wifilens.core.rf

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Vec2
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathLossTest {
    private fun openGrid(width: Int, height: Int = 1) = GridPlan(
        width = width,
        height = height,
        cells = List(width * height) { CellType.Floor(roomId = 1) },
    )

    @Test
    fun `router adjacent to device, no walls, rssi near reference`() {
        val plan = openGrid(width = 2)
        val result = predictRssi(plan, routerPos = Vec2(0, 0), targetPos = Vec2(1, 0))
        assertEquals(-40f, result, 0.001f)
    }

    @Test
    fun `one drywall cell between router and device subtracts its lossDb`() {
        val noWallPlan = openGrid(width = 3)
        val noWallResult = predictRssi(noWallPlan, routerPos = Vec2(0, 0), targetPos = Vec2(2, 0))

        val wallPlan = GridPlan(
            width = 3,
            height = 1,
            cells = listOf(
                CellType.Floor(roomId = 1),
                CellType.Empty(Material.Drywall),
                CellType.Floor(roomId = 1),
            ),
        )
        val wallResult = predictRssi(wallPlan, routerPos = Vec2(0, 0), targetPos = Vec2(2, 0))

        assertEquals(noWallResult - Material.Drywall.lossDb, wallResult, 0.001f)
    }

    @Test
    fun `two concrete cells subtract both lossDb values`() {
        val noWallPlan = openGrid(width = 4)
        val noWallResult = predictRssi(noWallPlan, routerPos = Vec2(0, 0), targetPos = Vec2(3, 0))

        val wallPlan = GridPlan(
            width = 4,
            height = 1,
            cells = listOf(
                CellType.Floor(roomId = 1),
                CellType.Empty(Material.Concrete),
                CellType.Empty(Material.Concrete),
                CellType.Floor(roomId = 1),
            ),
        )
        val wallResult = predictRssi(wallPlan, routerPos = Vec2(0, 0), targetPos = Vec2(3, 0))

        assertEquals(noWallResult - 2 * Material.Concrete.lossDb, wallResult, 0.001f)
    }

    @Test
    fun `zero distance does not throw and clamps to 1 metre`() {
        val plan = openGrid(width = 1)
        val result = predictRssi(plan, routerPos = Vec2(0, 0), targetPos = Vec2(0, 0))
        assertEquals(-40f, result, 0.001f)
    }

    @ParameterizedTest(name = "n={0}: rssi at distance does not exceed the n=2.0 baseline")
    @ValueSource(floats = [2.0f, 3.0f, 4.0f])
    fun `higher path loss exponent yields lower or equal rssi at a fixed distance`(n: Float) {
        val plan = openGrid(width = 6)
        val baseline = predictRssi(
            plan,
            routerPos = Vec2(0, 0),
            targetPos = Vec2(5, 0),
            pathLossExponent = 2.0f,
        )
        val result = predictRssi(
            plan,
            routerPos = Vec2(0, 0),
            targetPos = Vec2(5, 0),
            pathLossExponent = n,
        )
        assertTrue(result <= baseline, "n=$n gave $result, expected <= baseline $baseline")
    }
}
