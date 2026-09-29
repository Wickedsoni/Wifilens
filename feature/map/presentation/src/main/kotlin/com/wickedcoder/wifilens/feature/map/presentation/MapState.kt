package com.wickedcoder.wifilens.feature.map.presentation

import androidx.annotation.StringRes
import com.wickedcoder.wifilens.core.designsystem.UiText
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2

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
    /** Shown once in a Snackbar, then cleared with [MapAction.DismissError] (UI state, not a one-shot event). */
    val errorMessage: UiText? = null,
    /** Neutral hint shown once in a Snackbar (e.g. "Paint tiles to draw Kitchen"), cleared with [MapAction.DismissInfo]. */
    val infoMessage: UiText? = null,
    /** Undo/redo covers cell painting only (Room/Wall/Door/Erase) — not pin placement or room
     * creation, which already persist immediately and aren't meaningfully "undoable" in-memory. */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val haptics: HapticPrefs = HapticPrefs(),
) {
    val canRunDiagnosis: Boolean
        get() = rooms.isNotEmpty() && routerPos != null && devicePins.isNotEmpty()

    /** What's missing before Diagnose can run, for the disabled-state caption. */
    val missingForDiagnosis: List<MissingForDiagnosis>
        get() = buildList {
            if (rooms.isEmpty()) add(MissingForDiagnosis.Room)
            if (routerPos == null) add(MissingForDiagnosis.RouterPin)
            if (devicePins.isEmpty()) add(MissingForDiagnosis.DevicePin)
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

    data object DismissError : MapAction

    data object DismissInfo : MapAction
}

/** Which paint tool is active — determines what [MapAction.PaintCell] writes to the grid. */
enum class MapTool(
    @StringRes val label: Int,
) {
    Room(R.string.map_tool_room),
    Erase(R.string.map_tool_erase),
    Door(R.string.map_tool_door),
    Wall(R.string.map_tool_wall),
    Router(R.string.map_tool_router),
    Device(R.string.map_tool_device),
}

/** What Diagnose still needs, for the disabled Run-diagnosis caption. */
enum class MissingForDiagnosis(
    @StringRes val label: Int,
) {
    Room(R.string.map_missing_room),
    RouterPin(R.string.map_missing_router),
    DevicePin(R.string.map_missing_device),
}

/** Which rendering of the plan is on screen. */
internal enum class MapViewMode { TwoD, Iso }
