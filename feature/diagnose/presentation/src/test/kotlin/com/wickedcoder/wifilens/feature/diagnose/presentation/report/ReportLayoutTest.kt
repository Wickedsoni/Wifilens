package com.wickedcoder.wifilens.feature.diagnose.presentation.report

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportLayoutTest {
    private val wall = CellType.Empty(Material.Drywall)

    private fun plan(width: Int, height: Int, drawn: Map<Pair<Int, Int>, CellType>) =
        GridPlan(width, height, List(width * height) { i -> drawn[i % width to i / width] ?: wall })

    @Test
    fun `the report crops to the drawn home plus one cell of wall`() {
        val plan = plan(20, 20, mapOf((5 to 6) to CellType.Floor(1), (8 to 7) to CellType.Door, (8 to 9) to CellType.Floor(2)))

        assertEquals(CellBounds(minX = 4, minY = 5, maxX = 9, maxY = 10), plan.drawnBounds())
    }

    @Test
    fun `the wall margin stops at the grid edge`() {
        val plan = plan(4, 3, mapOf((0 to 0) to CellType.Floor(1), (3 to 2) to CellType.Floor(1)))

        val bounds = plan.drawnBounds()!!
        assertEquals(CellBounds(0, 0, 3, 2), bounds)
        assertEquals(4, bounds.width)
        assertEquals(3, bounds.height)
    }

    @Test
    fun `an empty plan has nothing to draw`() {
        assertNull(plan(5, 5, emptyMap()).drawnBounds())
    }
}
