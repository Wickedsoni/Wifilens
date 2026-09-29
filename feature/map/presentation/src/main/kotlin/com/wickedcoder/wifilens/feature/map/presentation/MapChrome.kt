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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.WifiLensBottomSheet
import com.wickedcoder.wifilens.core.designsystem.WifiLensChip
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensEmptyState
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensIconButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensToolSelector
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.MAX_NAME_LENGTH
import com.wickedcoder.wifilens.feature.map.domain.MAX_PLAN_SIZE
import com.wickedcoder.wifilens.feature.map.domain.MIN_PLAN_SIZE
import kotlin.math.PI

@Composable
internal fun TopBar(
    planName: String,
    onPlanNameClick: () -> Unit,
    viewMode: MapViewMode,
    onViewModeSelected: (MapViewMode) -> Unit,
    hasPlan: Boolean,
    onResetRequested: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The plan name opens the plans sheet (switch, rename, duplicate, export, import).
        val switchDescription = stringResource(R.string.map_plans_switch, planName)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onPlanNameClick)
                .semantics(mergeDescendants = true) { contentDescription = switchDescription }
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = planName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(WifiLensIcon.ArrowDropDown.vector, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        // Nothing to switch or undo before a plan exists.
        if (!hasPlan) return@Row
        // Wraps its content (no weight) so "2D | ISO" is never truncated; the plan name takes the leftover space.
        WifiLensSegmentedControl(
            items = listOf(stringResource(R.string.map_view_2d), stringResource(R.string.map_view_3d)),
            selectedIndex = viewMode.ordinal,
            onSelect = { onViewModeSelected(MapViewMode.entries[it]) },
            modifier = Modifier.padding(horizontal = WifiLensSpacing.xs),
        )
        WifiLensIconButton(
            icon = WifiLensIcon.Undo,
            contentDescription = stringResource(R.string.map_action_undo),
            onClick = onUndo,
            enabled = canUndo,
        )
        WifiLensIconButton(
            icon = WifiLensIcon.Redo,
            contentDescription = stringResource(R.string.map_action_redo),
            onClick = onRedo,
            enabled = canRedo,
        )
        WifiLensTextButton(text = stringResource(R.string.map_action_reset), onClick = onResetRequested)
    }
}

/**
 * ISO branch of the map. Owns the orbit angle and wall-rise animation so a drag recomposes only
 * this subtree, not the whole screen — and so leaving ISO drops both back to their initial values
 * (angle 0, walls flat) on the next entry with no explicit reset.
 */
@Composable
internal fun IsoViewport(
    plan: GridPlan,
    rooms: List<Room>,
    routerPos: Vec2?,
    devicePins: List<DevicePin>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val wallRise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        wallRise.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 350, easing = EaseOut))
    }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    // A pinch in progress is applied as a layer scale (no redraw per frame) and folded into zoomScale on release.
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var pinchActive by remember { mutableStateOf(false) }

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
                            pinchActive = true
                            val total = (zoomScale * pinchScale * event.calculateZoom()).coerceIn(MIN_ISO_ZOOM, MAX_ISO_ZOOM)
                            pinchScale = total / zoomScale
                        } else if (!pinching) {
                            // Finger left -> the near face of the plan follows it left, like dragging
                            // the map itself. A swipe across the full width = one full turn.
                            val dragX = event.calculatePan().x
                            rotationAngle = (rotationAngle - dragX / size.width * TWO_PI).mod(TWO_PI)
                        }
                        event.changes.forEach(PointerInputChange::consume)
                    } while (event.changes.any { it.pressed })
                    if (pinching) {
                        zoomScale *= pinchScale
                        pinchScale = 1f
                        pinchActive = false
                    }
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
            pinchScale = { pinchScale },
            pinching = pinchActive,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = stringResource(R.string.map_iso_hint),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = WifiLensSpacing.sm),
        )
    }
}

private const val MIN_ISO_ZOOM = 0.5f
private const val MAX_ISO_ZOOM = 3f

private val TWO_PI = (2.0 * PI).toFloat()

@Composable
internal fun ContextStrip(
    state: MapState,
    onAction: (MapAction) -> Unit,
    onNewRoomRequested: () -> Unit,
    onEditRoomRequested: () -> Unit,
    onWallMaterialRequested: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = WifiLensSpacing.md)) {
        when (state.activeTool) {
            MapTool.Room -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The selected chip already names the active room, so no separate label here.
                items(state.rooms, key = { it.id }) { room ->
                    WifiLensChip(
                        text = room.name,
                        selected = state.activeRoomId == room.id,
                        onClick = { onAction(MapAction.SelectRoom(room.id)) },
                    )
                }
                item {
                    // Adds a room to this plan (it doesn't start a new map); the VM selects it and hints to paint (B-30).
                    WifiLensChip(
                        text = stringResource(R.string.map_add_room),
                        selected = false,
                        onClick = onNewRoomRequested,
                        leadingIcon = { Icon(WifiLensIcon.Add.vector, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                if (state.activeRoomId != null) {
                    item { WifiLensChip(text = stringResource(R.string.map_edit_room), selected = false, onClick = onEditRoomRequested) }
                }
            }

            MapTool.Wall -> WifiLensChip(
                text = state.activeWallMaterial.displayName(),
                selected = true,
                onClick = onWallMaterialRequested,
            )

            MapTool.Router, MapTool.Device -> Text(
                stringResource(
                    if (state.activeTool == MapTool.Device) R.string.map_tap_floor_or_device else R.string.map_tap_floor_tile,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            MapTool.Door, MapTool.Erase, MapTool.Measure -> Unit // Measure has its own strip (SurveyStrip)
        }
    }
}

/** The Map editor's tool picker: an Expressive floating toolbar, centred above the canvas controls. */
@Composable
fun ToolDock(activeTool: MapTool, onToolSelected: (MapTool) -> Unit, modifier: Modifier = Modifier) {
    WifiLensToolSelector(
        items = MapTool.entries.map { stringResource(it.label) },
        selectedIndex = activeTool.ordinal,
        onSelect = { onToolSelected(MapTool.entries[it]) },
        modifier = modifier,
    )
}
