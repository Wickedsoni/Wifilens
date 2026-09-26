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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // SheetState default param, see NewRoomSheet
fun CreatePlanSheet(onDismiss: () -> Unit, onCreate: (width: Int, height: Int) -> Unit) {
    val colors = WifiLensTheme.colors
    var width by remember { mutableStateOf("20") }
    var height by remember { mutableStateOf("20") }
    var showError by remember { mutableStateOf(false) }
    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val valid = w != null && h != null && w in MIN_PLAN_SIZE..MAX_PLAN_SIZE && h in MIN_PLAN_SIZE..MAX_PLAN_SIZE

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Create floor plan", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = width,
                onValueChange = {
                    width = it.filter(Char::isDigit).take(3)
                    showError = false
                },
                label = { Text("Width (tiles)") },
                singleLine = true,
                isError = showError && (w == null || w !in MIN_PLAN_SIZE..MAX_PLAN_SIZE),
                modifier = Modifier.fillMaxWidth(),
            )
            NothingDivider(modifier = Modifier.padding(vertical = NothingSpacing.sm))
            OutlinedTextField(
                value = height,
                onValueChange = {
                    height = it.filter(Char::isDigit).take(3)
                    showError = false
                },
                label = { Text("Height (tiles)") },
                singleLine = true,
                isError = showError && (h == null || h !in MIN_PLAN_SIZE..MAX_PLAN_SIZE),
                modifier = Modifier.fillMaxWidth(),
            )
            if (showError) {
                Text(
                    "Width and height must each be between $MIN_PLAN_SIZE and $MAX_PLAN_SIZE tiles.",
                    style = NothingType.caption,
                    color = colors.accent,
                    modifier = Modifier.padding(top = NothingSpacing.sm),
                )
            }
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Create",
                onClick = { if (valid) onCreate(w!!, h!!) else showError = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

/** Mirrors the ViewModel's check so the sheet can explain a rejection instead of staying silent. */
private fun roomNameProblem(name: String, existingNames: List<String>): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a room name"
        existingNames.any { it.equals(trimmed, ignoreCase = true) } -> "A room called \"$trimmed\" already exists"
        else -> null
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
    val colors = WifiLensTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var showError by remember { mutableStateOf(false) }
    val problem = roomNameProblem(name, existingNames)

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Edit room", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    showError = false
                },
                label = { Text("Room name") },
                singleLine = true,
                isError = showError && problem != null,
                supportingText = if (showError && problem != null) ({ Text(problem) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Save",
                onClick = { if (problem == null) onRename(name.trim()) else showError = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Delete room", onClick = onDelete, modifier = Modifier.fillMaxWidth())
            Text(
                "Its tiles stay as unassigned floor.",
                style = NothingType.caption,
                color = colors.textDisabled,
                modifier = Modifier.padding(top = NothingSpacing.xs),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun ResetPlanSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = WifiLensTheme.colors
    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Reset floor plan?", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.sm))
            Text(
                "This deletes the plan, all rooms, the router pin and every device pin. It can't be undone.",
                style = NothingType.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(text = "Reset plan", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}

@Composable
// NothingBottomSheet's sheetState default (rememberModalBottomSheetState()) is inlined at the call site.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun NewRoomSheet(onDismiss: () -> Unit, existingNames: List<String>, onCreate: (name: String) -> Unit) {
    val colors = WifiLensTheme.colors
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val problem = roomNameProblem(name, existingNames)

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("New room", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    showError = false
                },
                label = { Text("Room name") },
                singleLine = true,
                isError = showError && problem != null,
                supportingText = if (showError && problem != null) ({ Text(problem) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Create",
                onClick = { if (problem == null) onCreate(name.trim()) else showError = true },
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
internal fun WallMaterialSheet(selected: Material, onDismiss: () -> Unit, onSelect: (Material) -> Unit) {
    val colors = WifiLensTheme.colors
    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Wall material", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            ALL_MATERIALS.forEach { material ->
                MaterialOptionRow(
                    label = material.displayName(),
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
internal fun NewDevicePinSheet(onDismiss: () -> Unit, onCreate: (name: String) -> Unit) {
    val colors = WifiLensTheme.colors
    var name by remember { mutableStateOf("") }

    NothingBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.sm)) {
            Text("Name this device", style = NothingType.heading, color = colors.textDisplay)
            Spacer(Modifier.height(NothingSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Device name") },
                singleLine = true,
                placeholder = { Text("e.g. Laptop, TV, Console") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.lg))
            NothingPrimaryButton(
                text = "Place device",
                onClick = { onCreate(name.trim().ifBlank { "Device" }) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NothingSpacing.sm))
            NothingGhostButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(NothingSpacing.lg))
        }
    }
}
