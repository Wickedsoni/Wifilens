package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room

/** [Room.id] used for a cell that hasn't been assigned to a room yet, e.g. after Erase. */
const val UNASSIGNED_ROOM_ID = 0

/** Longest room/device name accepted; keeps chips, labels and sheets from overflowing. */
const val MAX_NAME_LENGTH = 30

/** Smallest and largest plan edge (in tiles) the create-plan sheet accepts. */
const val MIN_PLAN_SIZE = 5
const val MAX_PLAN_SIZE = 200

private const val DEFAULT_DEVICE_NAME = "Device"

/** A new plan of [width] x [height] tiles with every tile an empty (wall-material) cell. */
fun blankPlan(width: Int, height: Int): GridPlan = GridPlan(
    width = width,
    height = height,
    cells = List(width * height) { CellType.Empty(Material.Drywall) },
)

/** Trimmed, length-capped device name; a blank one becomes "Device". */
fun normalizeDeviceName(name: String): String = name.trim().take(MAX_NAME_LENGTH).ifBlank { DEFAULT_DEVICE_NAME }

/** Business rules for rooms: naming and id allocation. */
object RoomRules {
    /**
     * A user-facing problem with [name], or null if it is acceptable. [exceptRoomId] excludes that
     * room from the duplicate check (renaming a room to its own name is fine).
     */
    fun nameProblem(name: String, rooms: List<Room>, exceptRoomId: Int? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> {
                "Enter a room name"
            }
            trimmed.length > MAX_NAME_LENGTH -> {
                "Room name is too long (max $MAX_NAME_LENGTH)"
            }
            rooms.any { it.id != exceptRoomId && it.name.equals(trimmed, ignoreCase = true) } -> {
                "A room called \"$trimmed\" already exists"
            }
            else -> {
                null
            }
        }
    }

    /** The id for the next room: one above the highest in use, never colliding with [UNASSIGNED_ROOM_ID]. */
    fun nextId(rooms: List<Room>): Int = (rooms.maxOfOrNull { it.id } ?: UNASSIGNED_ROOM_ID) + 1

    /** [plan] with every tile of [roomId] turned into unassigned floor. */
    fun unassignTiles(plan: GridPlan, roomId: Int): GridPlan = GridPlan(
        plan.width,
        plan.height,
        plan.cells.map { cell ->
            if (cell is CellType.Floor && cell.roomId == roomId) CellType.Floor(UNASSIGNED_ROOM_ID) else cell
        },
    )
}
