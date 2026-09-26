@file:OptIn(kotlinx.coroutines.FlowPreview::class) // debounce

package com.wickedcoder.wifilens.feature.map.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_ACTIVE_TOOL = "map_active_tool"
private const val KEY_ACTIVE_ROOM_ID = "map_active_room_id"
private const val KEY_ACTIVE_WALL_MATERIAL = "map_active_wall_material"
private const val MAX_UNDO_DEPTH = 20

/**
 * MVI ViewModel for the Map tab: owns the floor plan grid, rooms, and router/device pins.
 *
 * Two different persistence strategies are used here on purpose. Tool/room/material selection goes
 * in [savedStateHandle] — a few bytes, so it can survive process death for free. The grid itself
 * never does: a 40x40 plan is already 1,600 cells, and [SavedStateHandle] is a Bundle backed by
 * Binder IPC with a small transaction-size ceiling, so a plan that size risks a
 * `TransactionTooLargeException` if it's ever crammed in there. It's autosaved to Room instead (see
 * [pendingSave]), which has no such ceiling.
 */
class MapViewModel(
    private val repository: MapRepository,
    private val savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MapState(
            isLoading = true,
            activeTool = savedStateHandle.get<String>(KEY_ACTIVE_TOOL)?.let { MapTool.valueOf(it) } ?: MapTool.Room,
            activeRoomId = savedStateHandle.get<Int>(KEY_ACTIVE_ROOM_ID),
            activeWallMaterial = savedStateHandle.get<String>(KEY_ACTIVE_WALL_MATERIAL)?.toMaterial() ?: Material.Drywall,
        ),
    )
    val state: StateFlow<MapState> = _state.asStateFlow()

    private val _events = Channel<MapEvent>(Channel.BUFFERED)
    val events: Flow<MapEvent> = _events.receiveAsFlow()

    /** True once the in-memory [MapState.plan]/rooms diverge from what's persisted. */
    private var hasUnsavedChanges = false

    /** Bumped on every local edit. A save only clears [hasUnsavedChanges] if this is unchanged since
     * the save began — otherwise an edit made mid-save (e.g. a new room) would be treated as
     * persisted, and the next DB emission would overwrite it with the older saved snapshot. */
    private var editRevision = 0

    /** Cell-painting undo/redo only (see [MapState.canUndo] doc) — bounded so a long paint
     * session on a large grid doesn't hold unbounded full-grid snapshots in memory. */
    private val undoStack = ArrayDeque<GridPlan>()
    private val redoStack = ArrayDeque<GridPlan>()

    /**
     * Debounced autosave for cell paints. A 40x40 grid is 1,600 cells held only in [_state] —
     * on process death (minSdk 26, so [onCleared] is not guaranteed to run first) that vanishes
     * silently unless it's already in Room. 1.5s after the last paint, whatever's pending gets
     * flushed — long enough that a brief pause mid-stroke doesn't trigger a write; in-memory state
     * still updates on every tile. [persistIfDirty] (called from ON_STOP) is a second, immediate
     * safety net on top, so a longer wait doesn't widen the window for losing edits on backgrounding.
     */
    private val pendingSave = MutableSharedFlow<PendingSave>(extraBufferCapacity = 1)

    /** Bumped when the plan is cleared or replaced. A debounced save queued before that carries the
     * old epoch and is dropped — otherwise it fires up to 1.5s later and writes the discarded plan
     * back, resurrecting a plan the user just cleared. */
    private var planEpoch = 0

    init {
        viewModelScope.launch {
            pendingSave
                .debounce(1_500)
                .collect { (epoch, plan, rooms) ->
                    if (epoch != planEpoch) return@collect
                    try {
                        saveAndClearIfCurrent(plan, rooms)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // not just MapRepositoryException: a raw SQLiteException must not escape viewModelScope and crash the app
                        _events.send(MapEvent.ShowError(e.message ?: "Could not save floor plan"))
                    }
                }
        }

        settingsRepository.settings
            .onEach { settings ->
                _state.update {
                    it.copy(
                        haptics = HapticPrefs(
                            paint = settings.hapticsEnabled && settings.hapticPaint,
                            confirm = settings.hapticsEnabled && settings.hapticConfirm,
                            error = settings.hapticsEnabled && settings.hapticError,
                        ),
                    )
                }
            }.launchIn(viewModelScope)

        // Plan and rooms come from ONE flow: as two, a save briefly produced "new cells + old rooms",
        // which reset the active room to the first one and never restored it.
        combine(
            repository.observePlan(),
            repository.getRouterPin(),
            repository.getDevicePins(),
        ) { snapshot, routerPos, devicePins ->
            Quad(snapshot.plan, snapshot.rooms, routerPos, devicePins)
        }.onEach { (plan, rooms, routerPos, devicePins) ->
            // Only overwrite plan/rooms/pins from the DB while there are no unsaved local
            // edits — otherwise an in-progress paint would get clobbered by the last-saved
            // snapshot the moment the repo flow re-emits for an unrelated reason (e.g. a
            // pin placement, which does persist immediately).
            _state.update { current ->
                val effectiveRooms = if (hasUnsavedChanges) current.rooms else rooms
                current.copy(
                    plan = if (hasUnsavedChanges) current.plan else plan,
                    rooms = effectiveRooms,
                    // activeRoomId is null after a fresh launch (SavedStateHandle only survives
                    // process death) — without this, no chip is selected and paints land on
                    // UNASSIGNED_ROOM_ID, which draws as bare floor.
                    activeRoomId = resolveActiveRoomId(current.activeRoomId, effectiveRooms),
                    routerPos = routerPos,
                    devicePins = devicePins,
                    isLoading = false,
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun markDirty() {
        hasUnsavedChanges = true
        editRevision++
    }

    private suspend fun saveAndClearIfCurrent(plan: GridPlan, rooms: List<Room>) {
        val revisionAtSave = editRevision
        repository.savePlan(plan, rooms)
        if (editRevision == revisionAtSave) hasUnsavedChanges = false
    }

    fun onAction(action: MapAction) {
        when (action) {
            is MapAction.PaintCell -> paintCell(action.x, action.y)
            is MapAction.EraseCell -> setCell(action.x, action.y, CellType.Floor(UNASSIGNED_ROOM_ID))
            is MapAction.PlaceRouter -> placeRouter(action.x, action.y)
            is MapAction.PlaceDevice -> placeDevice(action.x, action.y, action.name)
            is MapAction.SelectTool -> selectTool(action.tool)
            is MapAction.SelectRoom -> selectRoom(action.roomId)
            is MapAction.SelectMaterial -> selectMaterial(action.material)
            is MapAction.CreateRoom -> createRoom(action.name)
            is MapAction.RenameRoom -> renameRoom(action.roomId, action.name)
            is MapAction.DeleteRoom -> deleteRoom(action.roomId)
            is MapAction.CreatePlan -> createPlan(action.width, action.height)
            MapAction.ClearPlan -> clearPlan()
            MapAction.Undo -> undo()
            MapAction.Redo -> redo()
        }
    }

    private fun paintCell(x: Int, y: Int) {
        val cellType = when (_state.value.activeTool) {
            MapTool.Room -> CellType.Floor(roomId = _state.value.activeRoomId ?: UNASSIGNED_ROOM_ID)
            MapTool.Wall -> CellType.Empty(_state.value.activeWallMaterial)
            MapTool.Door -> CellType.Door
            MapTool.Erase, MapTool.Router, MapTool.Device -> return // handled by their own actions
        }
        setCell(x, y, cellType)
    }

    private fun setCell(x: Int, y: Int, cellType: CellType) {
        val plan = _state.value.plan ?: return
        if (x !in 0 until plan.width || y !in 0 until plan.height) return

        val updatedPlan = plan.withCell(x, y, cellType)
        if (updatedPlan === plan) return // no-op paint, don't mark dirty

        undoStack.addLast(plan)
        if (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeFirst()
        redoStack.clear()

        markDirty()
        _state.update { it.copy(plan = updatedPlan, canUndo = true, canRedo = false) }
        pendingSave.tryEmit(PendingSave(planEpoch, updatedPlan, _state.value.rooms))
    }

    private fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        val current = _state.value.plan ?: return
        redoStack.addLast(current)
        markDirty()
        _state.update { it.copy(plan = previous, canUndo = undoStack.isNotEmpty(), canRedo = true) }
        pendingSave.tryEmit(PendingSave(planEpoch, previous, _state.value.rooms))
    }

    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val current = _state.value.plan ?: return
        undoStack.addLast(current)
        markDirty()
        _state.update { it.copy(plan = next, canUndo = true, canRedo = redoStack.isNotEmpty()) }
        pendingSave.tryEmit(PendingSave(planEpoch, next, _state.value.rooms))
    }

    private fun placeRouter(x: Int, y: Int) {
        val plan = _state.value.plan ?: return
        if (!plan.isWalkable(x, y)) return
        viewModelScope.launch {
            try {
                repository.setRouterPin(Vec2(x, y), band = "5")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // not just MapRepositoryException: a raw SQLiteException must not escape viewModelScope and crash the app
                _events.send(MapEvent.ShowError(e.message ?: "Could not place router pin"))
            }
        }
    }

    private fun placeDevice(x: Int, y: Int, name: String) {
        val plan = _state.value.plan ?: return
        if (!plan.isWalkable(x, y)) return
        viewModelScope.launch {
            try {
                repository.addDevicePin(Vec2(x, y), name.trim().take(MAX_NAME_LENGTH).ifBlank { "Device" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // not just MapRepositoryException: a raw SQLiteException must not escape viewModelScope and crash the app
                _events.send(MapEvent.ShowError(e.message ?: "Could not place device pin"))
            }
        }
    }

    private fun selectTool(tool: MapTool) {
        savedStateHandle[KEY_ACTIVE_TOOL] = tool.name
        _state.update { it.copy(activeTool = tool) }
    }

    private fun selectRoom(roomId: Int) {
        savedStateHandle[KEY_ACTIVE_ROOM_ID] = roomId
        _state.update { it.copy(activeRoomId = roomId, activeTool = MapTool.Room) }
    }

    private fun selectMaterial(material: Material) {
        savedStateHandle[KEY_ACTIVE_WALL_MATERIAL] = material.toWireName()
        _state.update { it.copy(activeWallMaterial = material) }
    }

    /**
     * Keeps [MapState.activeRoomId] pointing at a room that exists: the current one if it still
     * does, else the first remaining room, else null. Mirrored into [savedStateHandle] so a
     * process-death restore can't resurrect an id from a plan that has since been cleared.
     */
    private fun resolveActiveRoomId(current: Int?, rooms: List<Room>): Int? {
        val resolved = if (rooms.any { it.id == current }) current else rooms.firstOrNull()?.id
        savedStateHandle[KEY_ACTIVE_ROOM_ID] = resolved
        return resolved
    }

    /** Returns a user-facing problem with [name], or null if it is acceptable for [exceptRoomId]'s room. */
    private fun roomNameProblem(name: String, exceptRoomId: Int? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> {
                "Enter a room name"
            }
            trimmed.length > MAX_NAME_LENGTH -> {
                "Room name is too long (max $MAX_NAME_LENGTH)"
            }
            _state.value.rooms.any { it.id != exceptRoomId && it.name.equals(trimmed, ignoreCase = true) } -> {
                "A room called \"$trimmed\" already exists"
            }
            else -> {
                null
            }
        }
    }

    private fun renameRoom(roomId: Int, name: String) {
        val problem = roomNameProblem(name, exceptRoomId = roomId)
        if (problem != null) {
            _events.trySend(MapEvent.ShowError(problem))
            return
        }
        val updatedRooms = _state.value.rooms.map { if (it.id == roomId) it.copy(name = name.trim()) else it }
        markDirty()
        _state.update { it.copy(rooms = updatedRooms) }
        _state.value.plan?.let { plan -> pendingSave.tryEmit(PendingSave(planEpoch, plan, updatedRooms)) }
    }

    private fun deleteRoom(roomId: Int) {
        val plan = _state.value.plan ?: return
        if (_state.value.rooms.none { it.id == roomId }) return
        val updatedRooms = _state.value.rooms.filterNot { it.id == roomId }
        val updatedPlan = GridPlan(
            plan.width,
            plan.height,
            plan.cells.map { cell ->
                if (cell is CellType.Floor && cell.roomId == roomId) CellType.Floor(UNASSIGNED_ROOM_ID) else cell
            },
        )
        // Older snapshots still reference the deleted room, so undoing into them would resurrect tiles
        // with no room behind them.
        undoStack.clear()
        redoStack.clear()
        markDirty()
        _state.update {
            it.copy(
                plan = updatedPlan,
                rooms = updatedRooms,
                activeRoomId = resolveActiveRoomId(it.activeRoomId, updatedRooms),
                canUndo = false,
                canRedo = false,
            )
        }
        pendingSave.tryEmit(PendingSave(planEpoch, updatedPlan, updatedRooms))
    }

    private fun createRoom(name: String) {
        val problem = roomNameProblem(name)
        if (problem != null) {
            _events.trySend(MapEvent.ShowError(problem))
            return
        }
        val trimmedName = name.trim()
        val nextId = (_state.value.rooms.maxOfOrNull { it.id } ?: UNASSIGNED_ROOM_ID) + 1
        val updatedRooms = _state.value.rooms + Room(id = nextId, name = trimmedName)
        markDirty()
        savedStateHandle[KEY_ACTIVE_ROOM_ID] = nextId
        _state.update { it.copy(rooms = updatedRooms, activeRoomId = nextId, activeTool = MapTool.Room) }
        _state.value.plan?.let { plan -> pendingSave.tryEmit(PendingSave(planEpoch, plan, updatedRooms)) }
    }

    private fun createPlan(width: Int, height: Int) {
        val plan = GridPlan(
            width = width,
            height = height,
            cells = List(width * height) { CellType.Empty(Material.Drywall) },
        )
        planEpoch++
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                repository.savePlan(plan, rooms = emptyList())
                hasUnsavedChanges = false
                undoStack.clear()
                redoStack.clear()
                _state.update {
                    it.copy(
                        plan = plan,
                        rooms = emptyList(),
                        activeRoomId = resolveActiveRoomId(it.activeRoomId, emptyList()),
                        isLoading = false,
                        canUndo = false,
                        canRedo = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // not just MapRepositoryException: a raw SQLiteException must not escape viewModelScope and crash the app
                _state.update { it.copy(isLoading = false) }
                _events.send(MapEvent.ShowError(e.message ?: "Could not create plan"))
            }
        }
    }

    private fun clearPlan() {
        planEpoch++
        viewModelScope.launch {
            try {
                repository.clearPlan()
                hasUnsavedChanges = false
                undoStack.clear()
                redoStack.clear()
                _state.update {
                    it.copy(
                        plan = null,
                        rooms = emptyList(),
                        activeRoomId = resolveActiveRoomId(it.activeRoomId, emptyList()),
                        routerPos = null,
                        devicePins = emptyList(),
                        canUndo = false,
                        canRedo = false,
                    )
                }
                _events.send(MapEvent.PlanCleared)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // not just MapRepositoryException: a raw SQLiteException must not escape viewModelScope and crash the app
                _events.send(MapEvent.ShowError(e.message ?: "Could not clear plan"))
            }
        }
    }

    /**
     * Flushes in-memory paint edits to Room. Called from [MapScreen] on `ON_STOP` (the lifecycle
     * event Android guarantees before process death) — NOT relied on solely via [onCleared],
     * which fires when the ViewModelStore clears (e.g. the activity finishing) but is not
     * guaranteed when the OS kills a backgrounded process for memory.
     */
    fun persistIfDirty() {
        if (!hasUnsavedChanges) return
        val plan = _state.value.plan ?: return
        val rooms = _state.value.rooms
        viewModelScope.launch {
            try {
                saveAndClearIfCurrent(plan, rooms)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // not just MapRepositoryException: a raw SQLiteException must not escape viewModelScope and crash the app
                _events.send(MapEvent.ShowError(e.message ?: "Could not save floor plan"))
            }
        }
    }

    override fun onCleared() {
        // Best-effort save for the ordinary navigate-away case. Process death is covered by
        // persistIfDirty() on ON_STOP instead — see its doc comment.
        if (hasUnsavedChanges) {
            val plan = _state.value.plan
            val rooms = _state.value.rooms
            if (plan != null) {
                viewModelScope.launch { runCatching { repository.savePlan(plan, rooms) } }
            }
        }
    }
}

private data class PendingSave(val epoch: Int, val plan: GridPlan, val rooms: List<Room>)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun Material.toWireName(): String = when (this) {
    Material.Drywall -> "drywall"
    Material.Wood -> "wood"
    Material.Glass -> "glass"
    Material.Brick -> "brick"
    Material.Concrete -> "concrete"
    Material.Metal -> "metal"
}

private fun String.toMaterial(): Material = when (this) {
    "drywall" -> Material.Drywall
    "wood" -> Material.Wood
    "glass" -> Material.Glass
    "brick" -> Material.Brick
    "concrete" -> Material.Concrete
    "metal" -> Material.Metal
    else -> Material.Drywall
}
