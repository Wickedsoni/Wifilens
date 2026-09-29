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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensSwitchListItem
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
        BackHeader(title = stringResource(R.string.more_settings), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WifiLensSpacing.md),
        ) {
            SectionLabel(stringResource(R.string.more_settings_general), first = true)

            SettingRow(label = stringResource(R.string.more_settings_theme)) {
                WifiLensSegmentedControl(
                    items = listOf(
                        stringResource(R.string.more_settings_theme_system),
                        stringResource(R.string.more_settings_theme_dark),
                        stringResource(R.string.more_settings_theme_light),
                    ),
                    selectedIndex = settings.theme.ordinal,
                    onSelect = { viewModel.setTheme(ThemeMode.entries[it]) },
                    modifier = Modifier.padding(vertical = WifiLensSpacing.xs),
                )
            }

            // Wallpaper colours exist only on Android 12+, so the option is not offered below that.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WifiLensSwitchListItem(
                    headline = stringResource(R.string.more_settings_dynamic_colour),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            WifiLensSwitchListItem(
                headline = stringResource(R.string.more_settings_auto_scan),
                checked = settings.autoScanEnabled,
                onCheckedChange = viewModel::setAutoScanEnabled,
            )

            SectionLabel(stringResource(R.string.more_settings_feedback))

            WifiLensSwitchListItem(
                headline = stringResource(R.string.more_settings_haptics),
                checked = settings.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
            )

            // Always shown and greyed while the master switch is off (rather than hidden), so the
            // options stay discoverable and toggle state isn't lost from view.
            val subAlpha by animateFloatAsState(if (settings.hapticsEnabled) 1f else 0.4f, label = "haptic-sub-alpha")
            Column(modifier = Modifier.alpha(subAlpha)) {
                HapticSubRow(
                    label = stringResource(R.string.more_settings_haptic_paint),
                    description = stringResource(R.string.more_settings_haptic_paint_desc),
                    checked = settings.hapticPaint,
                    enabled = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticPaint,
                )
                HapticSubRow(
                    label = stringResource(R.string.more_settings_haptic_confirm),
                    description = stringResource(R.string.more_settings_haptic_confirm_desc),
                    checked = settings.hapticConfirm,
                    enabled = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticConfirm,
                )
                HapticSubRow(
                    label = stringResource(R.string.more_settings_haptic_error),
                    description = stringResource(R.string.more_settings_haptic_error_desc),
                    checked = settings.hapticError,
                    enabled = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticError,
                )
            }

            SectionLabel(stringResource(R.string.more_settings_prediction_model))
            val dbmUnit = stringResource(R.string.more_unit_dbm)

            SettingRow(
                label = stringResource(R.string.more_settings_path_loss),
                description = stringResource(R.string.more_settings_path_loss_desc),
                hint = stringResource(R.string.more_settings_path_loss_hint),
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
                label = stringResource(R.string.more_settings_reference_rssi),
                description = stringResource(R.string.more_settings_reference_rssi_desc),
                hint = stringResource(R.string.more_settings_reference_rssi_hint),
            ) {
                Stepper(
                    value = settings.referenceRssiAt1m,
                    range = -55f..-30f,
                    step = 1f,
                    format = { "${it.toInt()} $dbmUnit" },
                    onValueChange = viewModel::setReferenceRssiAt1m,
                )
            }

            WifiLensTextButton(
                text = stringResource(R.string.more_settings_reset_defaults),
                onClick = viewModel::resetPredictionModel,
                color = colors.danger,
                modifier = Modifier.padding(vertical = WifiLensSpacing.md),
            )

            SectionLabel(stringResource(R.string.more_settings_data))

            MoreRow(label = stringResource(R.string.more_settings_delete_plan), onClick = { showDeleteConfirm = true })
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.more_settings_delete_title)) },
            text = { Text(stringResource(R.string.more_settings_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFloorPlan()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.more_action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.more_action_cancel)) } },
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

/** Indented under the master stringResource(R.string.more_settings_haptics) row to show hierarchy; the whole row toggles (B-31). */
@Composable
private fun HapticSubRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    WifiLensSwitchListItem(
        headline = label,
        supporting = description,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(start = WifiLensSpacing.md),
    )
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
