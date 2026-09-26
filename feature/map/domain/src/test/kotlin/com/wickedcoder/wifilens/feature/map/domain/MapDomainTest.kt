package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDomainTest {
    private val rooms = listOf(Room(1, "Kitchen"), Room(2, "Bedroom"))

    @Test
    fun `blank names are rejected`() {
        assertEquals("Enter a room name", RoomRules.nameProblem("  ", rooms))
    }

    @Test
    fun `over-long names are rejected`() {
        assertEquals(
            "Room name is too long (max $MAX_NAME_LENGTH)",
            RoomRules.nameProblem("x".repeat(MAX_NAME_LENGTH + 1), rooms),
        )
    }

    @Test
    fun `duplicates are rejected case-insensitively but a room may keep its own name`() {
        assertEquals("A room called \"kitchen\" already exists", RoomRules.nameProblem(" kitchen ", rooms))
        assertNull(RoomRules.nameProblem("Kitchen", rooms, exceptRoomId = 1))
    }

    @Test
    fun `next room id is one above the highest and never zero`() {
        assertEquals(3, RoomRules.nextId(rooms))
        assertEquals(UNASSIGNED_ROOM_ID + 1, RoomRules.nextId(emptyList()))
    }

    @Test
    fun `deleting a room unassigns only its tiles`() {
        val plan = blankPlan(2, 1).withCell(0, 0, CellType.Floor(1)).withCell(1, 0, CellType.Floor(2))

        val result = RoomRules.unassignTiles(plan, 1)

        assertEquals(CellType.Floor(UNASSIGNED_ROOM_ID), result.cellAt(0, 0))
        assertEquals(CellType.Floor(2), result.cellAt(1, 0))
    }

    @Test
    fun `device names are trimmed capped and defaulted`() {
        assertEquals("Device", normalizeDeviceName("   "))
        assertEquals("TV", normalizeDeviceName(" TV "))
        assertEquals(MAX_NAME_LENGTH, normalizeDeviceName("y".repeat(99)).length)
    }

    @Test
    fun `history undoes and redoes in order and a new edit clears redo`() {
        val a = blankPlan(2, 2)
        val b = a.withCell(0, 0, CellType.Door)
        val history = EditHistory()
        history.record(a)

        assertTrue(history.canUndo)
        assertEquals(a, history.undo(b))
        assertTrue(history.canRedo)
        assertEquals(b, history.redo(a))

        history.undo(b)
        history.record(a)
        assertFalse(history.canRedo)
    }

    @Test
    fun `history depth is bounded`() {
        val history = EditHistory()
        repeat(MAX_UNDO_DEPTH + 5) { history.record(blankPlan(2, 2)) }

        var undone = 0
        while (history.undo(blankPlan(2, 2)) != null) undone++

        assertEquals(MAX_UNDO_DEPTH, undone)
    }
}
