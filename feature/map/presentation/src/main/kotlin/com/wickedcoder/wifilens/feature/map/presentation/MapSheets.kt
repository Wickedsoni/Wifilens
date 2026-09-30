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
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensIconButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.asString
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.MAX_NAME_LENGTH
import com.wickedcoder.wifilens.feature.map.domain.MAX_PLAN_SIZE
import com.wickedcoder.wifilens.feature.map.domain.MIN_PLAN_SIZE
import com.wickedcoder.wifilens.feature.map.domain.RoomRules
import kotlin.math.PI

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // SheetState default param, see NewRoomSheet
fun CreatePlanSheet(existingPlanCount: Int, onDismiss: () -> Unit, onCreate: (name: String, width: Int, height: Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val defaultName = if (existingPlanCount == 0) {
        stringResource(R.string.map_plan_default_name)
    } else {
        stringResource(R.string.map_plan_numbered, existingPlanCount + 1)
    }
    var name by remember { mutableStateOf(defaultName) }
    var width by remember { mutableStateOf("20") }
    var height by remember { mutableStateOf("20") }
    var showError by remember { mutableStateOf(false) }
    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val valid = w != null && h != null && w in MIN_PLAN_SIZE..MAX_PLAN_SIZE && h in MIN_PLAN_SIZE..MAX_PLAN_SIZE

    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_create_plan_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Text(
                stringResource(R.string.map_create_plan_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = WifiLensSpacing.xs),
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.map_plan_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            WifiLensDivider(modifier = Modifier.padding(vertical = WifiLensSpacing.sm))
            OutlinedTextField(
                value = width,
                onValueChange = {
                    width = it.filter(Char::isDigit).take(3)
                    showError = false
                },
                label = { Text(stringResource(R.string.map_width_tiles)) },
                singleLine = true,
                isError = showError && (w == null || w !in MIN_PLAN_SIZE..MAX_PLAN_SIZE),
                modifier = Modifier.fillMaxWidth(),
            )
            WifiLensDivider(modifier = Modifier.padding(vertical = WifiLensSpacing.sm))
            OutlinedTextField(
                value = height,
                onValueChange = {
                    height = it.filter(Char::isDigit).take(3)
                    showError = false
                },
                label = { Text(stringResource(R.string.map_height_tiles)) },
                singleLine = true,
                isError = showError && (h == null || h !in MIN_PLAN_SIZE..MAX_PLAN_SIZE),
                modifier = Modifier.fillMaxWidth(),
            )
            if (showError) {
                Text(
                    stringResource(R.string.map_plan_size_error, MIN_PLAN_SIZE, MAX_PLAN_SIZE),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.padding(top = WifiLensSpacing.sm),
                )
            }
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = stringResource(R.string.map_action_create),
                onClick = { if (valid && name.isNotBlank()) onCreate(name.trim(), w!!, h!!) else showError = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun EditRoomSheet(
    initialName: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initialName) }
    var showError by remember { mutableStateOf(false) }
    val problem = RoomRules.nameProblem(name, existingNames)?.toUiText()?.asString()

    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_edit_room), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    showError = false
                },
                label = { Text(stringResource(R.string.map_room_name)) },
                singleLine = true,
                isError = showError && problem != null,
                supportingText = if (showError && problem != null) ({ Text(problem) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = stringResource(R.string.map_action_save),
                onClick = { if (problem == null) onRename(name.trim()) else showError = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_delete_room), onClick = onDelete, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.map_delete_room_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = WifiLensSpacing.xs),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun ResetPlanSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_reset_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.sm))
            Text(
                stringResource(R.string.map_reset_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = stringResource(R.string.map_reset_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}

@Composable
// WifiLensBottomSheet's sheetState default (rememberBottomSheetState()) is inlined at the call site.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun NewRoomSheet(onDismiss: () -> Unit, existingNames: List<String>, onCreate: (name: String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val problem = RoomRules.nameProblem(name, existingNames)?.toUiText()?.asString()

    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_new_room_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    showError = false
                },
                label = { Text(stringResource(R.string.map_room_name)) },
                singleLine = true,
                isError = showError && problem != null,
                supportingText = if (showError && problem != null) ({ Text(problem) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = stringResource(R.string.map_action_create),
                onClick = { if (problem == null) onCreate(name.trim()) else showError = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // WifiLensBottomSheet's sheetState default, see NewRoomSheet
@Composable
internal fun WallMaterialSheet(selected: Material, onDismiss: () -> Unit, onSelect: (Material) -> Unit) {
    val colors = MaterialTheme.colorScheme
    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_wall_material_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.lg))
            ALL_MATERIALS.forEach { material ->
                MaterialOptionRow(
                    label = material.displayName(),
                    selected = material == selected,
                    onClick = { onSelect(material) },
                )
            }
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}

@Composable
private fun MaterialOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = WifiLensSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
        if (selected) Text("✓", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
    WifiLensDivider()
}

/** A placed device pin, tapped with the Device tool: remove it (to move a device, remove it and place it again). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // WifiLensBottomSheet's sheetState default, see NewRoomSheet
@Composable
internal fun DevicePinSheet(name: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.xs))
            Text(
                stringResource(R.string.map_device_remove_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = stringResource(R.string.map_device_remove),
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // WifiLensBottomSheet's sheetState default, see NewRoomSheet
@Composable
internal fun NewDevicePinSheet(onDismiss: () -> Unit, onCreate: (name: String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    val defaultDeviceName = stringResource(R.string.map_device_default_name)

    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_device_sheet_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.map_device_name)) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.map_device_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = stringResource(R.string.map_place_device),
                onClick = { onCreate(name.trim().ifBlank { defaultDeviceName }) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}
