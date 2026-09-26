package com.wickedcoder.wifilens.feature.map.presentation

import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2

/** [Room.id] used for a cell that hasn't been assigned to a room yet, e.g. after Erase. */
const val UNASSIGNED_ROOM_ID = 0

/** Haptic categories already resolved against the master switch, so the UI never re-checks it. */
data class HapticPrefs(
    val paint: Boolean = true,
    val confirm: Boolean = true,
    val error: Boolean = true,
)

/** MVI state for the Map tab: the grid, its rooms, router/device pins, and the currently active
 * tool. `null` [plan] means no floor plan has been created yet (the empty state). */
data class MapState(
    val plan: GridPlan? = null,
    val rooms: List<Room> = emptyList(),
    val routerPos: Vec2? = null,
    val devicePins: List<DevicePin> = emptyList(),
    val activeTool: MapTool = MapTool.Room,
    val activeRoomId: Int? = null,
    val activeWallMaterial: Material = Material.Drywall,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Undo/redo covers cell painting only (Room/Wall/Door/Erase) — not pin placement or room
     * creation, which already persist immediately and aren't meaningfully "undoable" in-memory. */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val haptics: HapticPrefs = HapticPrefs(),
) {
    val canRunDiagnosis: Boolean
        get() = rooms.isNotEmpty() && routerPos != null && devicePins.isNotEmpty()

    /** What's missing before Diagnose can run, for the disabled-state caption. */
    val missingForDiagnosis: List<String>
        get() = buildList {
            if (rooms.isEmpty()) add("a room")
            if (routerPos == null) add("a router pin")
            if (devicePins.isEmpty()) add("a device pin")
        }
}

/** User intents on the Map tab; handled by [MapViewModel.onAction]. */
sealed interface MapAction {
    data class PaintCell(val x: Int, val y: Int) : MapAction

    data class EraseCell(val x: Int, val y: Int) : MapAction

    data class PlaceRouter(val x: Int, val y: Int) : MapAction

    data class PlaceDevice(val x: Int, val y: Int, val name: String) : MapAction

    data class SelectTool(val tool: MapTool) : MapAction

    data class SelectRoom(val roomId: Int) : MapAction

    data class SelectMaterial(val material: Material) : MapAction

    /** The "+ New Room" chip in the context strip dispatches this. */
    data class CreateRoom(val name: String) : MapAction

    data class RenameRoom(val roomId: Int, val name: String) : MapAction

    /** Removes the room; its tiles become unassigned floor. */
    data class DeleteRoom(val roomId: Int) : MapAction

    data class CreatePlan(val width: Int, val height: Int) : MapAction

    data object ClearPlan : MapAction

    data object Undo : MapAction

    data object Redo : MapAction
}

/** Longest room/device name accepted; keeps chips, labels and sheets from overflowing. */
const val MAX_NAME_LENGTH = 30

/** Smallest and largest plan edge (in tiles) the create-plan sheet accepts. */
const val MIN_PLAN_SIZE = 5
const val MAX_PLAN_SIZE = 200

/** Which paint tool is active — determines what [MapAction.PaintCell] writes to the grid. */
enum class MapTool { Room, Erase, Door, Wall, Router, Device }

/** One-shot events from the Map tab (shown once, not part of persisted state). */
sealed interface MapEvent {
    data class ShowError(val message: String) : MapEvent

    data object PlanCleared : MapEvent
}
