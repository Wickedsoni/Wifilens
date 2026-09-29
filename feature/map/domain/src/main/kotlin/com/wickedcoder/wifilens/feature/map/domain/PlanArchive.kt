package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2

/** One plan with everything the user drew, as exported to / imported from a JSON file (ADR 0007). */
data class PlanArchive(
    val name: String,
    val plan: GridPlan,
    val rooms: List<Room>,
    val router: Vec2?,
    val devices: List<DevicePin>,
)

/** Why an import was rejected; the UI phrases it. Nothing is written when an import fails. */
enum class PlanImportProblem { NotAPlanFile, NewerVersion, InvalidContent }

class PlanImportException(val problem: PlanImportProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** Validation for an archive before it touches the database (pure, unit-tested). */
object PlanArchiveRules {
    fun problem(archive: PlanArchive): PlanImportProblem? {
        val valid = archive.hasValidSize() && archive.hasValidRooms() && archive.floorsReferenceKnownRooms() && archive.hasValidPins()
        return if (valid) null else PlanImportProblem.InvalidContent
    }

    private fun PlanArchive.hasValidSize(): Boolean =
        plan.width in MIN_PLAN_SIZE..MAX_PLAN_SIZE && plan.height in MIN_PLAN_SIZE..MAX_PLAN_SIZE &&
            plan.cells.size == plan.width * plan.height

    private fun PlanArchive.hasValidRooms(): Boolean {
        val ids = rooms.map { it.id }
        return UNASSIGNED_ROOM_ID !in ids && ids.distinct().size == ids.size && rooms.all { it.name.isValidName() }
    }

    private fun PlanArchive.floorsReferenceKnownRooms(): Boolean {
        val ids = rooms.map { it.id }.toSet()
        return plan.cells.all { it !is CellType.Floor || it.roomId == UNASSIGNED_ROOM_ID || it.roomId in ids }
    }

    /** Router and devices sit on walkable tiles inside the plan; device names are valid. */
    private fun PlanArchive.hasValidPins(): Boolean {
        val pins = listOfNotNull(router) + devices.map { it.pos }
        val onFloor = pins.all { it.x in 0 until plan.width && it.y in 0 until plan.height && plan.isWalkable(it.x, it.y) }
        return onFloor && devices.all { it.name.isValidName() }
    }

    private fun String.isValidName(): Boolean = isNotBlank() && length <= MAX_NAME_LENGTH
}
