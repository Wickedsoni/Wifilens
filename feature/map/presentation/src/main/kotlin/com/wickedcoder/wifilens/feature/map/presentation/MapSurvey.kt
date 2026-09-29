package com.wickedcoder.wifilens.feature.map.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.WifiLensBottomSheet
import com.wickedcoder.wifilens.core.designsystem.WifiLensChip
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensWavyProgress
import com.wickedcoder.wifilens.core.designsystem.signalColor

/**
 * The Measure tool's strip above the tool dock. Collecting [SurveyViewModel.liveRssi] here, and only here, means the
 * connection is polled only while this strip is on screen.
 */
@Composable
internal fun SurveyStripRoute(viewModel: SurveyViewModel, state: SurveyUiState, onClearRequested: () -> Unit) {
    val liveRssi by viewModel.liveRssi.collectAsStateWithLifecycle()
    SurveyStrip(state = state, liveRssi = liveRssi, onAction = viewModel::onAction, onClearRequested = onClearRequested)
}

@Composable
internal fun SurveyStrip(
    state: SurveyUiState,
    liveRssi: Int?,
    onAction: (SurveyAction) -> Unit,
    onClearRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = WifiLensSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            Text(
                text = liveRssi?.let { stringResource(R.string.map_survey_live, it) } ?: stringResource(R.string.map_survey_live_none),
                style = MaterialTheme.typography.titleSmall,
                color = liveRssi?.let { colors.signalColor(it.toFloat()) } ?: colors.onSurfaceVariant,
            )
            Text(
                text = if (state.measuringAt != null) {
                    stringResource(R.string.map_survey_measuring)
                } else {
                    pluralStringResource(R.plurals.map_survey_count, state.measurements.size, state.measurements.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.measuringAt != null) {
            WifiLensWavyProgress(progress = { state.progress })
        } else if (state.measurements.isEmpty()) {
            Text(stringResource(R.string.map_survey_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (state.measurements.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                item {
                    WifiLensChip(
                        text = state.calibration?.let { stringResource(R.string.map_survey_calibrated_badge, it.rmseDb) }
                            ?: stringResource(R.string.map_survey_calibrate),
                        selected = state.calibration != null,
                        onClick = { onAction(SurveyAction.Calibrate) },
                    )
                }
                item { WifiLensChip(text = stringResource(R.string.map_survey_clear), selected = false, onClick = onClearRequested) }
            }
        }
    }
}

@Composable
// WifiLensBottomSheet's sheetState default (rememberModalBottomSheetState()) is inlined at the call site.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun ClearReadingsSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(stringResource(R.string.map_survey_clear_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.sm))
            Text(
                stringResource(R.string.map_survey_clear_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(text = stringResource(R.string.map_survey_clear), onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.sm))
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}
