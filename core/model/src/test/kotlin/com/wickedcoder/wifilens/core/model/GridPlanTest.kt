package com.wickedcoder.wifilens.core.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GridPlanTest {
    private val everyKind: List<CellType> = listOf(
        CellType.Empty(Material.Drywall),
        CellType.Empty(Material.Wood),
        CellType.Empty(Material.Glass),
        CellType.Empty(Material.Brick),
        CellType.Empty(Material.Concrete),
        CellType.Empty(Material.Metal),
        CellType.Floor(0),
        CellType.Floor(1),
        CellType.Floor(7),
        CellType.Door,
    )

    private fun plan(cells: List<CellType>, width: Int = cells.size) = GridPlan(width, cells.size / width, cells)

    @Test
    fun `every cell type round-trips through the compact storage`() {
        val plan = plan(everyKind)
        everyKind.forEachIndexed { x, expected -> assertEquals(expected, plan.cellAt(x, 0)) }
        assertEquals(everyKind, plan.cells)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 5, 6, 7, 9])
    fun `cells view matches cellAt for every index`(index: Int) {
        val plan = plan(everyKind, width = 5)
        assertEquals(plan.cellAt(index % 5, index / 5), plan.cells[index])
    }

    @Test
    fun `withCell returns a new plan and leaves the original untouched`() {
        val original = plan(List(4) { CellType.Empty(Material.Drywall) }, width = 2)
        val edited = original.withCell(1, 1, CellType.Floor(3))

        assertEquals(CellType.Floor(3), edited.cellAt(1, 1))
        assertEquals(CellType.Empty(Material.Drywall), original.cellAt(1, 1))
        assertNotEquals(original, edited)
    }

    @Test
    fun `withCell to the same value returns the same instance`() {
        val original = plan(listOf(CellType.Floor(2), CellType.Door), width = 2)
        assertSame(original, original.withCell(0, 0, CellType.Floor(2)))
    }

    @Test
    fun `replacing a floor with a wall clears its room id so equal plans compare equal`() {
        val a = plan(listOf(CellType.Floor(5), CellType.Door), width = 2).withCell(0, 0, CellType.Empty(Material.Wood))
        val b = plan(listOf(CellType.Empty(Material.Wood), CellType.Door), width = 2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `plans built from equal cells are equal, different cells are not`() {
        assertEquals(plan(everyKind), plan(everyKind))
        assertNotEquals(plan(everyKind), plan(everyKind).withCell(0, 0, CellType.Door))
    }

    @Test
    fun `walkability is unchanged - floors and doors yes, walls no`() {
        val plan = plan(listOf(CellType.Floor(0), CellType.Door, CellType.Empty(Material.Metal)), width = 3)
        assertTrue(plan.isWalkable(0, 0))
        assertTrue(plan.isWalkable(1, 0))
        assertFalse(plan.isWalkable(2, 0))
    }

    @Test
    fun `size mismatch and out-of-bounds are rejected as before`() {
        assertFailsWith<IllegalArgumentException> { GridPlan(2, 2, listOf(CellType.Door)) }
        val plan = plan(listOf(CellType.Door, CellType.Door), width = 2)
        assertFailsWith<IllegalArgumentException> { plan.cellAt(2, 0) }
        assertFailsWith<IllegalArgumentException> { plan.withCell(0, 1, CellType.Door) }
    }
}
