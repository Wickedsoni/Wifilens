package com.wickedcoder.wifilens.feature.more.presentation

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensSwitch
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.model.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        BackHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WifiLensSpacing.md),
        ) {
            SectionLabel("General", first = true)

            SettingRow(label = "Theme") {
                WifiLensSegmentedControl(
                    items = listOf("System", "Dark", "Light"),
                    selectedIndex = settings.theme.ordinal,
                    onSelect = { viewModel.setTheme(ThemeMode.entries[it]) },
                    modifier = Modifier.padding(vertical = WifiLensSpacing.xs),
                )
            }

            // Wallpaper colours exist only on Android 12+, so the option is not offered below that.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MoreRow(label = "Dynamic colour", trailing = {
                    WifiLensSwitch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                })
            }

            MoreRow(label = "Auto-scan", trailing = {
                WifiLensSwitch(checked = settings.autoScanEnabled, onCheckedChange = viewModel::setAutoScanEnabled)
            })

            SectionLabel("Feedback")

            MoreRow(label = "Haptics", trailing = {
                WifiLensSwitch(checked = settings.hapticsEnabled, onCheckedChange = viewModel::setHapticsEnabled)
            })

            // Always shown and greyed while the master switch is off (rather than hidden), so the
            // options stay discoverable and toggle state isn't lost from view.
            val subAlpha by animateFloatAsState(if (settings.hapticsEnabled) 1f else 0.4f, label = "haptic-sub-alpha")
            Column(modifier = Modifier.alpha(subAlpha)) {
                HapticSubRow(
                    label = "Paint feedback",
                    description = "A tick for each new tile painted",
                    checked = settings.hapticPaint,
                    enabled = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticPaint,
                )
                HapticSubRow(
                    label = "Confirm actions",
                    description = "Pin placed, plan created",
                    checked = settings.hapticConfirm,
                    enabled = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticConfirm,
                )
                HapticSubRow(
                    label = "Error feedback",
                    description = "Invalid placement, like a pin on a wall",
                    checked = settings.hapticError,
                    enabled = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticError,
                )
            }

            SectionLabel("Prediction model")

            SettingRow(
                label = "Path-loss exponent (n)",
                description = "Controls how fast signal fades with distance. Free space = 2.0. " +
                    "A typical home with walls and furniture = 3.0 (default). " +
                    "Dense walls or many obstructions = 4.0+. " +
                    "Higher = signal drops faster over distance.",
                hint = "LOWER = OPTIMISTIC  ·  HIGHER = CONSERVATIVE",
            ) {
                Stepper(
                    value = settings.pathLossExponent,
                    range = 2.0f..4.5f,
                    step = 0.1f,
                    format = { "%.1f".format(it) },
                    onValueChange = viewModel::setPathLossExponent,
                )
            }

            SettingRow(
                label = "Reference RSSI at 1 m (A)",
                description = "Signal strength measured 1 metre from your router. " +
                    "Most home routers: −40 to −50 dBm (default −40). " +
                    "Check your router's spec sheet, or measure with the Analyze tab " +
                    "while standing 1 m away from the router.",
                hint = "CLOSER TO 0 = STRONGER ROUTER  ·  MORE NEGATIVE = WEAKER ROUTER",
            ) {
                Stepper(
                    value = settings.referenceRssiAt1m,
                    range = -55f..-30f,
                    step = 1f,
                    format = { "${it.toInt()} dBm" },
                    onValueChange = viewModel::setReferenceRssiAt1m,
                )
            }

            WifiLensTextButton(
                text = "Reset to defaults",
                onClick = viewModel::resetPredictionModel,
                color = colors.danger,
                modifier = Modifier.padding(vertical = WifiLensSpacing.md),
            )

            SectionLabel("Data")

            MoreRow(label = "Delete floor plan", onClick = { showDeleteConfirm = true })
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete floor plan?") },
            text = { Text("This removes your plan, rooms, and pins. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFloorPlan()
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String, first: Boolean = false) {
    WifiLensLabel(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = if (first) WifiLensSpacing.xs else WifiLensSpacing.lg, bottom = WifiLensSpacing.xs),
    )
}

@Composable
private fun SettingRow(
    label: String,
    description: String? = null,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        content()
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = WifiLensSpacing.sm),
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = WifiLensSpacing.xs),
            )
        }
        WifiLensDivider(modifier = Modifier.padding(top = WifiLensSpacing.sm))
    }
}

/** Indented under the master "Haptics" row to show hierarchy. */
@Composable
private fun HapticSubRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(start = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            WifiLensSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        WifiLensDivider()
    }
}

@Composable
private fun Stepper(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = WifiLensSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable { onValueChange((value - step).coerceIn(range)) }) {
            Text("−", style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceVariant)
        }
        Text(format(value), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Box(modifier = Modifier.clickable { onValueChange((value + step).coerceIn(range)) }) {
            Text("+", style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceVariant)
        }
    }
}
