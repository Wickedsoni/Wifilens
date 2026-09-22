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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.NothingBottomSheet
import com.wickedcoder.wifilens.core.designsystem.NothingChip
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingEmptyState
import com.wickedcoder.wifilens.core.designsystem.NothingGhostButton
import com.wickedcoder.wifilens.core.designsystem.NothingIcon
import com.wickedcoder.wifilens.core.designsystem.NothingIconButton
import com.wickedcoder.wifilens.core.designsystem.NothingPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.NothingSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.feature.map.domain.DevicePin
import com.wickedcoder.wifilens.feature.map.domain.Room
import org.koin.androidx.compose.koinViewModel
import kotlin.math.PI

private enum class MapViewMode { TwoD, Iso }

@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { /* wire to a snackbar host if/when the screen has one */ }
    }

    MapContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
private fun MapContent(
    state: MapState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    val haptics = LocalHapticFeedback.current
    var viewMode by remember { mutableStateOf(MapViewMode.TwoD) }
    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showNewRoomDialog by remember { mutableStateOf(false) }
    var showWallMaterialSheet by remember { mutableStateOf(false) }
    var pendingDevicePos by remember { mutableStateOf<Vec2?>(null) }

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        TopBar(
            planName = if (state.plan != null) "Home" else "No plan",
            viewMode = viewMode,
            onViewModeSelected = { viewMode = it },
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
                onWallMaterialRequested = { showWallMaterialSheet = true },
            )
            ToolDock(activeTool = state.activeTool, onToolSelected = { onAction(MapAction.SelectTool(it)) })

            NothingGhostButton(
                text = if (state.canRunDiagnosis) {
                    "Run diagnosis"
                } else {
                    "Run diagnosis — needs " + state.missingForDiagnosis.joinToString(", ")
                },
                onClick = { /* wired when :feature:diagnose exists */ },
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
            onCreate = { name ->
                onAction(MapAction.CreateRoom(name))
                showNewRoomDialog = false
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

@Composable
private fun TopBar(
    planName: String,
    viewMode: MapViewMode,
    onViewModeSelected: (MapViewMode) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val colors = WifiLensTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = planName.uppercase(),
            style = NothingType.label,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Boxed with weight so the segmented control gets a bounded slot instead of expanding
        // across the whole row and squeezing its siblings.
        Box(modifier = Modifier.weight(1f).padding(horizontal = NothingSpacing.sm), contentAlignment = Alignment.Center) {
            NothingSegmentedControl(
                items = listOf("2D", "ISO"),
                selectedIndex = viewMode.ordinal,
                onSelect = { onViewModeSelected(MapViewMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NothingIconButton(icon = NothingIcon.Undo, contentDescription = "Undo", onClick = onUndo, enabled = canUndo)
        NothingIconButton(icon = NothingIcon.Redo, contentDescription = "Redo", onClick = onRedo, enabled = canRedo)
    }
}

/**
 * ISO branch of the map. Owns the orbit angle and wall-rise animation so a drag recomposes only
 * this subtree, not the whole screen — and so leaving ISO drops both back to their initial values
 * (angle 0, walls flat) on the next entry with no explicit reset.
 */
@Composable
private fun IsoViewport(
    plan: GridPlan,
    rooms: List<Room>,
    routerPos: Vec2?,
    devicePins: List<DevicePin>,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    val wallRise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        wallRise.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 350, easing = EaseOut))
    }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    // Orbit and pinch share ONE pointerInput (same reasoning as MapCanvas): a drag detector consumes
    // its pointer's movement, which cancels a sibling detectTransformGestures, so pinches would never
    // arrive. Finger count picks the mode instead — one finger orbits, two or more zoom.
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput("iso-gestures") {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Once a second finger lands the rest of the gesture is a pinch, even if it drops
                    // back to one finger — that finger must not start orbiting.
                    var pinching = false
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            pinching = true
                            zoomScale = (zoomScale * event.calculateZoom()).coerceIn(MIN_ISO_ZOOM, MAX_ISO_ZOOM)
                        } else if (!pinching) {
                            // Finger left -> the near face of the plan follows it left, like dragging
                            // the map itself. A swipe across the full width = one full turn.
                            val dragX = event.calculatePan().x
                            rotationAngle = (rotationAngle - dragX / size.width * TWO_PI).mod(TWO_PI)
                        }
                        event.changes.forEach(PointerInputChange::consume)
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        IsoCanvas(
            plan = plan,
            rooms = rooms,
            routerPos = routerPos,
            devicePins = devicePins,
            wallRiseProgress = wallRise.value,
            rotationAngle = rotationAngle,
            zoomScale = zoomScale,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "← SWIPE TO ORBIT →  ·  PINCH TO ZOOM",
            style = NothingType.label,
            color = colors.textDisabled,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = NothingSpacing.sm),
        )
    }
}

private const val MIN_ISO_ZOOM = 0.5f
private const val MAX_ISO_ZOOM = 3f

private val TWO_PI = (2.0 * PI).toFloat()

@Composable
private fun ContextStrip(
    state: MapState,
    onAction: (MapAction) -> Unit,
    onNewRoomRequested: () -> Unit,
    onWallMaterialRequested: () -> Unit,
) {
    val colors = WifiLensTheme.colors
    // maxWidth here is already net of the horizontal padding, which is what the active-room label is capped at.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = NothingSpacing.md)) {
        when (state.activeTool) {
            MapTool.Room -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Looked up from state on every recomposition — never cached or defaulted to the first room.
                val activeRoomName = state.rooms.find { it.id == state.activeRoomId }?.name
                if (activeRoomName != null) {
                    item {
                        Text(
                            text = activeRoomName.uppercase(),
                            style = NothingType.label,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // A LazyRow item is measured with unbounded width, so ellipsis needs an explicit cap.
                            modifier = Modifier.widthIn(max = maxWidth),
                        )
                    }
                }
                items(state.rooms, key = { it.id }) { room ->
                    NothingChip(
                        text = room.name,
                        selected = state.activeRoomId == room.id,
                        onClick = { onAction(MapAction.SelectRoom(room.id)) },
                    )
                }
                item { NothingChip(text = "+ New room", selected = false, onClick = onNewRoomRequested) }
            }

            MapTool.Wall -> NothingChip(
                text = state.activeWallMaterial::class.simpleName.orEmpty(),
                selected = true,
                onClick = onWallMaterialRequested,
            )

            MapTool.Router, MapTool.Device -> Text(
                "Tap a floor tile",
                style = NothingType.caption,
                color = colors.textDisabled,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            MapTool.Door, MapTool.Erase -> Unit
        }
    }
}

@Composable
private fun ToolDock(activeTool: MapTool, onToolSelected: (MapTool) -> Unit) {
    val colors = WifiLensTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(vertical = NothingSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MapTool.entries.forEach { tool ->
            val selected = tool == activeTool
            Box(
                modifier = Modifier
                    .background(if (selected) colors.textDisplay else colors.surface)
                    .clickable { onToolSelected(tool) }
                    .padding(horizontal = NothingSpacing.sm, vertical = NothingSpacing.xs),
            ) {
                Text(
                    text = tool.name.uppercase(),
                    style = NothingType.label,
                    color = if (selected) colors.black else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // SheetState default param, see NewRoomSheet
private fun CreatePlanSheet(onDismiss: () -> Unit, onCreate: (width: Int, height: Int) -> Unit) {
    val colors = WifiLensTheme.colors
    var width by remember { mutableStateOf("20") }
    var height by remember { mutableStateOf("20") }

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Create floor plan", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = width,
                onValueChange = { width = it },
                label = { Text("Width (tiles)") },
                modifier = Modifier.fillMaxWidth(),
            )
            NothingDivider(modifier = Modifier.padding(vertical = NothingSpacing.sm))
            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                label = { Text("Height (tiles)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Create",
                onClick = {
                    val w = width.toIntOrNull()?.coerceIn(1, 200) ?: return@NothingPrimaryButton
                    val h = height.toIntOrNull()?.coerceIn(1, 200) ?: return@NothingPrimaryButton
                    onCreate(w, h)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // NothingBottomSheet's sheetState default (rememberModalBottomSheetState()) is inlined at the call site
private fun NewRoomSheet(onDismiss: () -> Unit, onCreate: (name: String) -> Unit) {
    val colors = WifiLensTheme.colors
    var name by remember { mutableStateOf("") }

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("New room", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Room name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Create",
                onClick = { if (name.isNotBlank()) onCreate(name) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

private val ALL_MATERIALS = listOf(
    Material.Drywall,
    Material.Wood,
    Material.Glass,
    Material.Brick,
    Material.Concrete,
    Material.Metal,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // NothingBottomSheet's sheetState default, see NewRoomSheet
@Composable
private fun WallMaterialSheet(selected: Material, onDismiss: () -> Unit, onSelect: (Material) -> Unit) {
    val colors = WifiLensTheme.colors
    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Wall material", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            ALL_MATERIALS.forEach { material ->
                MaterialOptionRow(
                    label = material::class.simpleName.orEmpty(),
                    selected = material == selected,
                    onClick = { onSelect(material) },
                )
            }
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

@Composable
private fun MaterialOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WifiLensTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = NothingSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NothingType.body, color = if (selected) colors.textDisplay else colors.textSecondary)
        if (selected) Text("✓", style = NothingType.body, color = colors.textDisplay)
    }
    NothingDivider()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // NothingBottomSheet's sheetState default, see NewRoomSheet
@Composable
private fun NewDevicePinSheet(onDismiss: () -> Unit, onCreate: (name: String) -> Unit) {
    val colors = WifiLensTheme.colors
    var name by remember { mutableStateOf("") }

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Name this device", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Device name") },
                placeholder = { Text("e.g. Laptop, TV, Console") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Place device",
                onClick = { onCreate(name.ifBlank { "Device" }) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun MapEmptyPreview() {
    WifiLensTheme {
        MapContent(state = MapState(), onAction = {})
    }
}
