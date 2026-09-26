package com.wickedcoder.wifilens.feature.map.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.NothingBottomSheet
import com.wickedcoder.wifilens.core.designsystem.NothingChip
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingEmptyState
import com.wickedcoder.wifilens.core.designsystem.NothingErrorSnackbar
import com.wickedcoder.wifilens.core.designsystem.NothingGhostButton
import com.wickedcoder.wifilens.core.designsystem.NothingIcon
import com.wickedcoder.wifilens.core.designsystem.NothingIconButton
import com.wickedcoder.wifilens.core.designsystem.NothingPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.NothingSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.MAX_NAME_LENGTH
import com.wickedcoder.wifilens.feature.map.domain.MAX_PLAN_SIZE
import com.wickedcoder.wifilens.feature.map.domain.MIN_PLAN_SIZE
import org.koin.androidx.compose.koinViewModel
import kotlin.math.PI

/** Explicit names: `::class.simpleName` is renamed by R8 in release builds, which would show garbage. */
internal fun Material.displayName(): String = when (this) {
    Material.Drywall -> "Drywall"
    Material.Wood -> "Wood"
    Material.Glass -> "Glass"
    Material.Brick -> "Brick"
    Material.Concrete -> "Concrete"
    Material.Metal -> "Metal"
}

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel(),
    onRunDiagnosis: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // ON_STOP is the lifecycle event Android guarantees before process death — flush unsaved
    // paint edits there, not only in onCleared() (see MapViewModel.persistIfDirty doc).
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentViewModel.persistIfDirty()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is MapEvent.ShowError) errorMessage = event.message
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        MapContent(state = state, onAction = viewModel::onAction, onRunDiagnosis = onRunDiagnosis)
        NothingErrorSnackbar(
            message = errorMessage,
            onDismiss = { errorMessage = null },
            modifier = Modifier.align(Alignment.BottomCenter).padding(NothingSpacing.md),
        )
    }
}

@Composable
private fun MapContent(
    state: MapState,
    onAction: (MapAction) -> Unit,
    onRunDiagnosis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    val haptics = LocalHapticFeedback.current
    var viewMode by remember { mutableStateOf(MapViewMode.TwoD) }
    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showNewRoomDialog by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showWallMaterialSheet by remember { mutableStateOf(false) }
    var pendingDevicePos by remember { mutableStateOf<Vec2?>(null) }

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        TopBar(
            planName = if (state.plan != null) "Home" else "No plan",
            viewMode = viewMode,
            onViewModeSelected = { viewMode = it },
            hasPlan = state.plan != null,
            onResetRequested = { showResetDialog = true },
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = { onAction(MapAction.Undo) },
            onRedo = { onAction(MapAction.Redo) },
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.plan == null -> NothingEmptyState(
                    title = "No floor plan yet",
                    description = "Create a floor plan to start mapping your Wi-Fi coverage.",
                    action = { NothingPrimaryButton(text = "Create plan", onClick = { showCreatePlanDialog = true }) },
                )

                viewMode == MapViewMode.Iso -> IsoViewport(
                    plan = state.plan,
                    rooms = state.rooms,
                    routerPos = state.routerPos,
                    devicePins = state.devicePins,
                )

                else -> MapCanvas(
                    plan = state.plan,
                    rooms = state.rooms,
                    routerPos = state.routerPos,
                    devicePins = state.devicePins,
                    paintHaptics = state.haptics.paint,
                    onCellTouched = { x, y, isDrag ->
                        val isPinTool = state.activeTool == MapTool.Router || state.activeTool == MapTool.Device
                        if (isPinTool && !state.plan.isWalkable(x, y)) {
                            // Pins only go on floor/door tiles; the placement is dropped either way.
                            // Only a deliberate tap earns the error buzz — a drag sweeping across
                            // walls would fire it on every cell crossed.
                            if (!isDrag && state.haptics.error) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        } else if (state.activeTool == MapTool.Device) {
                            // Stage the tap and ask for a name instead of placing immediately —
                            // every device pin was previously hardcoded to "Device".
                            pendingDevicePos = Vec2(x, y)
                        } else {
                            onAction(cellTouchAction(state.activeTool, x, y))
                            if (state.activeTool == MapTool.Router && state.haptics.confirm) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (state.plan != null) {
            ContextStrip(
                state = state,
                onAction = onAction,
                onNewRoomRequested = { showNewRoomDialog = true },
                onEditRoomRequested = { showEditRoomDialog = true },
                onWallMaterialRequested = { showWallMaterialSheet = true },
            )
            ToolDock(activeTool = state.activeTool, onToolSelected = { onAction(MapAction.SelectTool(it)) })

            NothingGhostButton(
                text = if (state.canRunDiagnosis) {
                    "Run diagnosis"
                } else {
                    "Run diagnosis — needs " + state.missingForDiagnosis.joinToString(", ")
                },
                onClick = { if (state.canRunDiagnosis) onRunDiagnosis() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm),
            )
        }
    }

    if (showCreatePlanDialog) {
        CreatePlanSheet(
            onDismiss = { showCreatePlanDialog = false },
            onCreate = { width, height ->
                onAction(MapAction.CreatePlan(width, height))
                if (state.haptics.confirm) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                showCreatePlanDialog = false
            },
        )
    }

    if (showNewRoomDialog) {
        NewRoomSheet(
            onDismiss = { showNewRoomDialog = false },
            existingNames = state.rooms.map { it.name },
            onCreate = { name ->
                onAction(MapAction.CreateRoom(name))
                showNewRoomDialog = false
            },
        )
    }

    if (showEditRoomDialog) {
        val room = state.rooms.find { it.id == state.activeRoomId }
        if (room == null) {
            showEditRoomDialog = false
        } else {
            EditRoomSheet(
                initialName = room.name,
                existingNames = state.rooms.filter { it.id != room.id }.map { it.name },
                onDismiss = { showEditRoomDialog = false },
                onRename = { name ->
                    onAction(MapAction.RenameRoom(room.id, name))
                    showEditRoomDialog = false
                },
                onDelete = {
                    onAction(MapAction.DeleteRoom(room.id))
                    showEditRoomDialog = false
                },
            )
        }
    }

    if (showResetDialog) {
        ResetPlanSheet(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                onAction(MapAction.ClearPlan)
                showResetDialog = false
            },
        )
    }

    if (showWallMaterialSheet) {
        WallMaterialSheet(
            selected = state.activeWallMaterial,
            onDismiss = { showWallMaterialSheet = false },
            onSelect = { material ->
                onAction(MapAction.SelectMaterial(material))
                showWallMaterialSheet = false
            },
        )
    }

    pendingDevicePos?.let { pos ->
        NewDevicePinSheet(
            onDismiss = { pendingDevicePos = null },
            onCreate = { name ->
                onAction(MapAction.PlaceDevice(pos.x, pos.y, name))
                if (state.haptics.confirm) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                pendingDevicePos = null
            },
        )
    }
}

/** MapTool.Device is intercepted before this is called (see MapContent's onCellTouched) so a name
 * can be collected first — the branch stays here only so this `when` is exhaustive. */
private fun cellTouchAction(tool: MapTool, x: Int, y: Int): MapAction = when (tool) {
    MapTool.Room, MapTool.Wall, MapTool.Door -> MapAction.PaintCell(x, y)
    MapTool.Erase -> MapAction.EraseCell(x, y)
    MapTool.Router -> MapAction.PlaceRouter(x, y)
    MapTool.Device -> MapAction.PlaceDevice(x, y, name = "Device")
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun MapEmptyPreview() {
    WifiLensTheme {
        MapContent(state = MapState(), onAction = {}, onRunDiagnosis = {})
    }
}
