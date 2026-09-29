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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.wickedcoder.wifilens.core.designsystem.WifiLensErrorSnackbar
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensIconButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensInfoSnackbar
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.asString
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
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    surveyViewModel: SurveyViewModel = hiltViewModel(),
    onRunDiagnosis: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val survey by surveyViewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(survey.completedReadings) {
        if (survey.completedReadings > 0 && state.haptics.confirm) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        MapContent(
            state = state,
            onAction = viewModel::onAction,
            onRunDiagnosis = onRunDiagnosis,
            survey = survey,
            onSurveyAction = surveyViewModel::onAction,
            surveyStrip = { onClearRequested -> SurveyStripRoute(surveyViewModel, survey, onClearRequested) },
        )
        WifiLensErrorSnackbar(
            message = state.errorMessage?.asString(),
            onDismiss = { viewModel.onAction(MapAction.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(WifiLensSpacing.md),
        )
        WifiLensInfoSnackbar(
            message = state.infoMessage?.asString(),
            onDismiss = { viewModel.onAction(MapAction.DismissInfo) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(WifiLensSpacing.md),
        )
        WifiLensErrorSnackbar(
            message = survey.errorMessage?.asString(),
            onDismiss = { surveyViewModel.onAction(SurveyAction.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(WifiLensSpacing.md),
        )
        WifiLensInfoSnackbar(
            message = survey.infoMessage?.asString(),
            onDismiss = { surveyViewModel.onAction(SurveyAction.DismissInfo) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(WifiLensSpacing.md),
        )
    }
}

@Composable
private fun MapContent(
    state: MapState,
    onAction: (MapAction) -> Unit,
    onRunDiagnosis: () -> Unit,
    modifier: Modifier = Modifier,
    survey: SurveyUiState = SurveyUiState(),
    onSurveyAction: (SurveyAction) -> Unit = {},
    /** The Measure tool's strip; a slot so only the screen (not previews) subscribes to the live signal. */
    surveyStrip: @Composable (onClearRequested: () -> Unit) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var viewMode by remember { mutableStateOf(MapViewMode.TwoD) }
    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showPlansSheet by remember { mutableStateOf(false) }
    var showNewRoomDialog by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showWallMaterialSheet by remember { mutableStateOf(false) }
    var pendingDevicePos by remember { mutableStateOf<Vec2?>(null) }
    var showClearReadingsDialog by remember { mutableStateOf(false) }
    val isMeasuring = state.activeTool == MapTool.Measure

    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        TopBar(
            planName = state.planName ?: stringResource(R.string.map_plan_none),
            onPlanNameClick = { showPlansSheet = true },
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
                state.plan == null -> WifiLensEmptyState(
                    title = stringResource(R.string.map_empty_title),
                    description = stringResource(R.string.map_empty_body),
                    action = {
                        WifiLensPrimaryButton(
                            text = stringResource(R.string.map_empty_action),
                            onClick = { showCreatePlanDialog = true },
                        )
                    },
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
                    paintHaptics = state.haptics.paint && !isMeasuring,
                    measurements = if (isMeasuring) survey.measurements else emptyList(),
                    measuringAt = survey.measuringAt,
                    onCellTouched = { x, y, isDrag ->
                        val needsFloor = state.activeTool in FLOOR_ONLY_TOOLS
                        if (isMeasuring && isDrag) {
                            // A reading is a deliberate tap on the spot you're standing on; a drag measures nothing.
                        } else if (needsFloor && !state.plan.isWalkable(x, y)) {
                            // Pins only go on floor/door tiles; the placement is dropped either way.
                            // Only a deliberate tap earns the error buzz — a drag sweeping across
                            // walls would fire it on every cell crossed.
                            if (!isDrag && state.haptics.error) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        } else if (isMeasuring) {
                            onSurveyAction(SurveyAction.Measure(Vec2(x, y)))
                        } else if (state.activeTool == MapTool.Device) {
                            // Stage the tap and ask for a name instead of placing immediately —
                            // every device pin was previously hardcoded to "Device".
                            pendingDevicePos = Vec2(x, y)
                        } else {
                            cellTouchAction(state.activeTool, x, y)?.let(onAction)
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
            if (isMeasuring) {
                surveyStrip { showClearReadingsDialog = true }
            } else {
                ContextStrip(
                    state = state,
                    onAction = onAction,
                    onNewRoomRequested = { showNewRoomDialog = true },
                    onEditRoomRequested = { showEditRoomDialog = true },
                    onWallMaterialRequested = { showWallMaterialSheet = true },
                )
            }
            ToolDock(
                activeTool = state.activeTool,
                onToolSelected = { onAction(MapAction.SelectTool(it)) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = WifiLensSpacing.xs, vertical = WifiLensSpacing.xs),
            )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
                WifiLensPrimaryButton(
                    text = stringResource(R.string.map_run_diagnosis),
                    onClick = onRunDiagnosis,
                    enabled = state.canRunDiagnosis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.canRunDiagnosis) {
                    Text(
                        text = stringResource(
                            R.string.map_needs,
                            state.missingForDiagnosis
                                .map { stringResource(it.label) }
                                .joinToString(stringResource(R.string.map_list_separator)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = WifiLensSpacing.xs),
                    )
                }
            }
        }
    }

    if (showCreatePlanDialog) {
        CreatePlanSheet(
            existingPlanCount = state.plans.size,
            onDismiss = { showCreatePlanDialog = false },
            onCreate = { name, width, height ->
                onAction(MapAction.CreatePlan(name, width, height))
                if (state.haptics.confirm) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                showCreatePlanDialog = false
            },
        )
    }

    if (showPlansSheet) {
        PlansSheet(
            plans = state.plans,
            onDismiss = { showPlansSheet = false },
            onAction = onAction,
            onNewPlan = {
                showPlansSheet = false
                showCreatePlanDialog = true
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

    if (showClearReadingsDialog) {
        ClearReadingsSheet(
            onDismiss = { showClearReadingsDialog = false },
            onConfirm = {
                onSurveyAction(SurveyAction.ClearMeasurements)
                showClearReadingsDialog = false
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

/** Tools whose taps only make sense on floor or door tiles (a pin, a reading); walls are rejected with a buzz. */
private val FLOOR_ONLY_TOOLS = setOf(MapTool.Router, MapTool.Device, MapTool.Measure)

/** Device and Measure are intercepted before this is called (see MapContent's onCellTouched): Device collects a
 * name first, Measure goes to the survey. Their branches keep this `when` exhaustive. */
private fun cellTouchAction(tool: MapTool, x: Int, y: Int): MapAction? = when (tool) {
    MapTool.Room, MapTool.Wall, MapTool.Door -> MapAction.PaintCell(x, y)
    MapTool.Erase -> MapAction.EraseCell(x, y)
    MapTool.Router -> MapAction.PlaceRouter(x, y)
    MapTool.Device -> MapAction.PlaceDevice(x, y, name = "") // blank: the domain (normalizeDeviceName) picks the default
    MapTool.Measure -> null
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun MapEmptyPreview() {
    WifiLensTheme {
        MapContent(state = MapState(), onAction = {}, onRunDiagnosis = {})
    }
}
