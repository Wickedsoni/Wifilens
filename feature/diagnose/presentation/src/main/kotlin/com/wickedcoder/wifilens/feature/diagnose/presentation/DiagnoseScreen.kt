package com.wickedcoder.wifilens.feature.diagnose.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.StatusDot
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensEmptyState
import com.wickedcoder.wifilens.core.designsystem.WifiLensErrorSnackbar
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.WifiLensWavyProgress
import com.wickedcoder.wifilens.core.designsystem.asString
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.core.designsystem.warning
import com.wickedcoder.wifilens.feature.diagnose.domain.Finding
import com.wickedcoder.wifilens.feature.diagnose.domain.Severity
import com.wickedcoder.wifilens.feature.diagnose.presentation.report.ShareReportButton
import com.wickedcoder.wifilens.feature.diagnose.presentation.report.findingText
import com.wickedcoder.wifilens.feature.diagnose.presentation.report.shareReport

@Composable
fun DiagnoseScreen(
    modifier: Modifier = Modifier,
    viewModel: DiagnoseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnoseContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
private fun DiagnoseContent(
    state: DiagnoseState,
    onAction: (DiagnoseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.report_chooser)
    LaunchedEffect(state.reportToShare) {
        state.reportToShare?.let { report ->
            context.shareReport(report, chooserTitle)
            onAction(DiagnoseAction.ReportShared)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        DiagnoseBody(state = state, onAction = onAction)
        WifiLensErrorSnackbar(
            message = state.errorMessage?.asString(),
            onDismiss = { onAction(DiagnoseAction.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(WifiLensSpacing.md),
        )
    }
}

@Composable
private fun DiagnoseBody(
    state: DiagnoseState,
    onAction: (DiagnoseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        Column(modifier = Modifier.padding(WifiLensSpacing.md)) {
            WifiLensSegmentedControl(
                items = listOf(
                    stringResource(R.string.diagnose_tab_coverage),
                    stringResource(R.string.diagnose_tab_best_spot),
                    stringResource(R.string.diagnose_tab_speed),
                    stringResource(R.string.diagnose_tab_signal),
                ),
                selectedIndex = when (state.tab) {
                    DiagnoseTab.Coverage -> 0
                    DiagnoseTab.BestSpot -> 1
                    DiagnoseTab.Speed -> 2
                    DiagnoseTab.Signal -> 3
                },
                onSelect = {
                    onAction(
                        when (it) {
                            0 -> DiagnoseAction.TabCoverage
                            1 -> DiagnoseAction.TabBestSpot
                            2 -> DiagnoseAction.TabSpeed
                            else -> DiagnoseAction.TabSignal
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.padding(top = WifiLensSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
            ) {
                if (state.tab is DiagnoseTab.Speed) {
                    Text(
                        stringResource(R.string.diagnose_badge_measured),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.diagnose_speed_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else if (state.tab is DiagnoseTab.Signal) {
                    Text(
                        stringResource(R.string.diagnose_badge_live),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.diagnose_signal_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else if (state.calibration != null) {
                    Text(
                        stringResource(R.string.diagnose_badge_calibrated),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                    Text(
                        stringResource(R.string.diagnose_calibrated_intro, state.calibration.rmseDb),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.diagnose_badge_predicted),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.diagnose_predicted_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        // The speed test doesn't depend on the floor plan, so it must not sit behind this gate.
        if (state.tab is DiagnoseTab.Speed) {
            SpeedTab(state, onAction)
            return
        }
        if (state.tab is DiagnoseTab.Signal) {
            SignalTab()
            return
        }

        if (state.plan == null || state.routerPos == null) {
            WifiLensEmptyState(
                title = stringResource(R.string.diagnose_empty_title),
                description = stringResource(R.string.diagnose_empty_body),
            )
            return
        }

        when (state.tab) {
            DiagnoseTab.Coverage -> CoverageTab(state, onAction)
            DiagnoseTab.BestSpot -> BestSpotTab(state, onAction)
            DiagnoseTab.Speed, DiagnoseTab.Signal -> Unit // handled above, before the floor-plan gate
        }
    }
}

@Composable
private fun CoverageTab(state: DiagnoseState, onAction: (DiagnoseAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var selectedRoomId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(0.45f)) {
            CoverageMapCanvas(
                plan = state.plan!!,
                coverage = state.coverage,
                routerPos = state.routerPos,
                devicePins = state.devicePins,
                selectedRoomId = selectedRoomId,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            stringResource(R.string.diagnose_legend),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.sm).padding(top = WifiLensSpacing.sm),
        )
        // Coverage is recomputed automatically whenever the plan, pins or model settings change, so
        // there is nothing to trigger — say so instead of offering a button that would do nothing.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = WifiLensSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.diagnose_coverage_auto),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ShareReportButton(exporting = state.isExportingReport, onShare = { onAction(DiagnoseAction.ShareReport(it)) })
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(0.55f),
            contentPadding = androidx.compose.foundation.layout
                .PaddingValues(WifiLensSpacing.md),
        ) {
            item {
                state.worstDevice?.let { (pin, rssi) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = WifiLensSpacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${rssi.toInt()}", style = MaterialTheme.typography.displaySmall, color = rssiColor(rssi, colors))
                            Text(
                                " " + stringResource(R.string.diagnose_unit_dbm),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(WifiLensSpacing.sm))
                            Text(
                                stringResource(R.string.diagnose_badge_predicted),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        WifiLensLabel(
                            stringResource(R.string.diagnose_weakest_device, pin.name),
                            modifier = Modifier.padding(top = WifiLensSpacing.xs),
                        )
                    }
                }
            }

            items(state.roomSummaries) { room ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedRoomId = if (selectedRoomId == room.roomId) null else room.roomId }
                        .padding(vertical = WifiLensSpacing.sm),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            room.name ?: stringResource(R.string.diagnose_room_unnamed, room.roomId),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                        )
                        Text(
                            stringResource(R.string.diagnose_value_dbm, room.avgRssi.toInt()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = rssiColor(room.avgRssi, colors),
                        )
                    }
                    WifiLensDivider(modifier = Modifier.padding(top = WifiLensSpacing.sm))
                }
            }

            if (state.findings.isNotEmpty()) {
                item { Spacer(Modifier.height(WifiLensSpacing.lg)) }
                items(state.findings) { finding ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = WifiLensSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
                    ) {
                        StatusDot(
                            color = if (finding.severity == Severity.Poor) colors.danger else colors.warning,
                            contentDescription = stringResource(
                                if (finding.severity == Severity.Poor) R.string.diagnose_severity_poor else R.string.diagnose_severity_fair,
                            ),
                        )
                        Text(findingText(finding), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    }
                    WifiLensDivider()
                }
            }
        }
    }
}

@Composable
private fun BestSpotTab(state: DiagnoseState, onAction: (DiagnoseAction) -> Unit) {
    val colors = MaterialTheme.colorScheme

    when (val optimizer = state.optimizerState) {
        OptimizerState.Idle -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WifiLensPrimaryButton(
                    text = stringResource(R.string.diagnose_find_best_spot),
                    onClick = { onAction(DiagnoseAction.RunOptimizer) },
                )
            }
        }

        is OptimizerState.Running -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(WifiLensSpacing.md),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WifiLensWavyProgress(progress = { optimizer.progress })
                Text(
                    stringResource(R.string.diagnose_evaluating, (optimizer.progress * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = WifiLensSpacing.sm),
                )
            }
        }

        OptimizerState.Complete -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(0.5f)) {
                    BestSpotMapCanvas(
                        plan = state.plan!!,
                        tileScores = state.tileScores,
                        bestTile = state.bestTile,
                        routerPos = state.routerPos,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Centred under the centred map, so the result reads as one block with it.
                Column(
                    modifier = Modifier.fillMaxWidth().weight(0.5f).padding(WifiLensSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val gain = state.bestTileGainDb
                    Text(
                        text = if (gain != null) {
                            stringResource(R.string.diagnose_gain_db, gain)
                        } else {
                            stringResource(R.string.diagnose_value_none)
                        },
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.success,
                        textAlign = TextAlign.Center,
                    )
                    val worstNow = state.worstDevice?.second
                    val worstBest = state.bestTile?.let { state.tileScores[it] }
                    if (worstNow != null && worstBest != null) {
                        Text(
                            stringResource(R.string.diagnose_worst_device_change, worstNow.toInt(), worstBest.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = WifiLensSpacing.xs),
                        )
                    }
                    Spacer(Modifier.height(WifiLensSpacing.lg))
                    state.bestTile?.let { tile ->
                        WifiLensPrimaryButton(
                            text = stringResource(R.string.diagnose_move_router),
                            onClick = { onAction(DiagnoseAction.MoveRouter(tile)) },
                        )
                    }
                }
            }
        }

        OptimizerState.AlreadyOptimal -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.diagnose_already_optimal),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = WifiLensSpacing.md),
                )
            }
        }
    }
}

@Composable
private fun SpeedTab(state: DiagnoseState, onAction: (DiagnoseAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val test = state.speedTest

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WifiLensSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
    ) {
        val downloadMbps = when (test) {
            is SpeedTestState.Running -> test.mbps
            is SpeedTestState.Finished -> test.mbps
            else -> null
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.1f".format(downloadMbps ?: 0f),
                style = MaterialTheme.typography.displayMedium,
                color = when {
                    test is SpeedTestState.Finished -> colors.success
                    downloadMbps == null -> colors.onSurfaceVariant
                    else -> colors.onSurface
                },
            )
            Text(
                " " + stringResource(R.string.diagnose_unit_mbps),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = WifiLensSpacing.xs),
            )
        }
        WifiLensLabel(stringResource(R.string.diagnose_download_speed))

        val previous = state.previousSpeedMbps
        if (test is SpeedTestState.Finished && previous != null && previous > 0f) {
            val changePct = ((test.mbps - previous) / previous * 100f).toInt()
            val (text, color) = when {
                changePct > 0 -> stringResource(R.string.diagnose_speed_faster, changePct, previous) to colors.success
                changePct < 0 -> stringResource(R.string.diagnose_speed_slower, -changePct, previous) to colors.danger
                else -> stringResource(R.string.diagnose_speed_unchanged, previous) to colors.onSurfaceVariant
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }

        Spacer(Modifier.height(WifiLensSpacing.sm))
        WifiLensDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.diagnose_link_speed), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(
                text = state.linkSpeedMbps?.let { stringResource(R.string.diagnose_link_value, it) }
                    ?: stringResource(if (state.isOnWifi) R.string.diagnose_link_unknown else R.string.diagnose_link_not_wifi),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        WifiLensDivider()
        Text(
            stringResource(R.string.diagnose_speed_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = WifiLensSpacing.sm),
        )

        Spacer(Modifier.height(WifiLensSpacing.lg))

        when (test) {
            is SpeedTestState.Running -> {
                WifiLensWavyProgress(progress = { test.progress })
                Text(
                    stringResource(R.string.diagnose_testing),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            is SpeedTestState.Failed -> {
                Text(test.reason.asString(), style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            }
            else -> {
                Unit
            }
        }
        WifiLensPrimaryButton(
            text = stringResource(if (test is SpeedTestState.Finished) R.string.diagnose_test_again else R.string.diagnose_run_speed_test),
            onClick = { onAction(DiagnoseAction.RunSpeedTest) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(WifiLensSpacing.lg))
        SpeedHistorySection()
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun DiagnoseEmptyPreview() {
    WifiLensTheme {
        DiagnoseContent(state = DiagnoseState(), onAction = {})
    }
}

@Composable
private fun findingText(finding: Finding): String = LocalResources.current.findingText(finding)
