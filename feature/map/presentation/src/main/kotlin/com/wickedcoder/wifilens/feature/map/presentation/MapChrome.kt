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

@Composable
internal fun TopBar(
    planName: String,
    viewMode: MapViewMode,
    onViewModeSelected: (MapViewMode) -> Unit,
    hasPlan: Boolean,
    onResetRequested: () -> Unit,
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
        // Nothing to switch or undo before a plan exists.
        if (!hasPlan) return@Row
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
        Text(
            text = "RESET",
            style = NothingType.label,
            color = colors.textSecondary,
            modifier = Modifier
                .clickable(onClickLabel = "Reset floor plan", onClick = onResetRequested)
                .padding(horizontal = NothingSpacing.sm, vertical = NothingSpacing.md),
        )
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
internal fun ContextStrip(
    state: MapState,
    onAction: (MapAction) -> Unit,
    onNewRoomRequested: () -> Unit,
    onEditRoomRequested: () -> Unit,
    onWallMaterialRequested: () -> Unit,
) {
    val colors = WifiLensTheme.colors
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = NothingSpacing.md)) {
        when (state.activeTool) {
            MapTool.Room -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The selected chip already names the active room, so no separate label here.
                items(state.rooms, key = { it.id }) { room ->
                    NothingChip(
                        text = room.name,
                        selected = state.activeRoomId == room.id,
                        onClick = { onAction(MapAction.SelectRoom(room.id)) },
                    )
                }
                item { NothingChip(text = "+ New room", selected = false, onClick = onNewRoomRequested) }
                if (state.activeRoomId != null) {
                    item { NothingChip(text = "Edit room", selected = false, onClick = onEditRoomRequested) }
                }
            }

            MapTool.Wall -> NothingChip(
                text = state.activeWallMaterial.displayName(),
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
fun ToolDock(activeTool: MapTool, onToolSelected: (MapTool) -> Unit) {
    val colors = WifiLensTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = NothingSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
    ) {
        MapTool.entries.forEach { tool ->
            val selected = tool == activeTool
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp) // minimum touch target
                    .background(if (selected) colors.textDisplay else colors.surface)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onToolSelected(tool) })
                    .padding(horizontal = NothingSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tool.name.uppercase(),
                    style = NothingType.label,
                    maxLines = 1,
                    softWrap = false,
                    color = if (selected) colors.black else colors.textSecondary,
                )
            }
        }
    }
}
