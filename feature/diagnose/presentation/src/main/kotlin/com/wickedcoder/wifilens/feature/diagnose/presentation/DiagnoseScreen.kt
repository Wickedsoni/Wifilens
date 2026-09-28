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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.core.designsystem.warning
import com.wickedcoder.wifilens.feature.diagnose.domain.Severity

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
    Box(modifier = modifier.fillMaxSize()) {
        DiagnoseBody(state = state, onAction = onAction)
        WifiLensErrorSnackbar(
            message = state.errorMessage,
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
                items = listOf("Coverage", "Best spot", "Speed"),
                selectedIndex = when (state.tab) {
                    DiagnoseTab.Coverage -> 0
                    DiagnoseTab.BestSpot -> 1
                    DiagnoseTab.Speed -> 2
                },
                onSelect = {
                    onAction(
                        when (it) {
                            0 -> DiagnoseAction.TabCoverage
                            1 -> DiagnoseAction.TabBestSpot
                            else -> DiagnoseAction.TabSpeed
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
                    Text("[MEASURED]", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(
                        "Real speed of your current Wi-Fi connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Text("[PREDICTED]", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(
                        "Estimate from your plan, not a measurement.",
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

        if (state.plan == null || state.routerPos == null) {
            WifiLensEmptyState(
                title = "Nothing to diagnose yet",
                description = "Create a floor plan with a router and at least one device pin first.",
            )
            return
        }

        when (state.tab) {
            DiagnoseTab.Coverage -> CoverageTab(state)
            DiagnoseTab.BestSpot -> BestSpotTab(state, onAction)
            DiagnoseTab.Speed -> Unit // handled above, before the floor-plan gate
        }
    }
}

@Composable
private fun CoverageTab(state: DiagnoseState) {
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
            "GOOD ≥ -67 · FAIR -67 TO -75 · POOR < -75",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.sm).padding(top = WifiLensSpacing.sm),
        )
        // Coverage is recomputed automatically whenever the plan, pins or model settings change, so
        // there is nothing to trigger — say so instead of offering a button that would do nothing.
        Text(
            "COVERAGE IS COMPUTED FROM YOUR FLOOR PLAN",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(WifiLensSpacing.sm),
        )

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
                            Text(" DBM", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            Spacer(Modifier.width(WifiLensSpacing.sm))
                            Text("[PREDICTED]", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        WifiLensLabel("Weakest device: ${pin.name}", modifier = Modifier.padding(top = WifiLensSpacing.xs))
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
                        Text(room.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        Text(
                            "${room.avgRssi.toInt()} dBm",
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
                    ) {
                        StatusDot(color = if (finding.severity == Severity.Poor) colors.danger else colors.warning)
                        Text(finding.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
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
                WifiLensPrimaryButton(text = "Find best router spot", onClick = { onAction(DiagnoseAction.RunOptimizer) })
            }
        }

        is OptimizerState.Running -> {
            Column(modifier = Modifier.fillMaxSize().padding(WifiLensSpacing.md), verticalArrangement = Arrangement.Center) {
                WifiLensWavyProgress(progress = { optimizer.progress })
                Text(
                    "EVALUATING ${(optimizer.progress * 100).toInt()}%",
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
                Column(modifier = Modifier.fillMaxWidth().weight(0.5f).padding(WifiLensSpacing.md)) {
                    val gain = state.bestTileGainDb
                    Text(
                        text = if (gain != null) "+${"%.1f".format(gain)} dB" else "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.success,
                    )
                    val worstNow = state.worstDevice?.second
                    val worstBest = state.bestTile?.let { state.tileScores[it] }
                    if (worstNow != null && worstBest != null) {
                        Text(
                            "WORST DEVICE ${worstNow.toInt()} → ${worstBest.toInt()} (PREDICTED)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = WifiLensSpacing.xs),
                        )
                    }
                    Spacer(Modifier.height(WifiLensSpacing.lg))
                    state.bestTile?.let { tile ->
                        WifiLensPrimaryButton(
                            text = "Move router here",
                            onClick = { onAction(DiagnoseAction.MoveRouter(tile)) },
                        )
                    }
                }
            }
        }

        OptimizerState.AlreadyOptimal -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Current spot is already the best tile.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
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
                " MBPS",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = WifiLensSpacing.xs),
            )
        }
        WifiLensLabel("Download speed")

        val previous = state.previousSpeedMbps
        if (test is SpeedTestState.Finished && previous != null && previous > 0f) {
            val changePct = ((test.mbps - previous) / previous * 100f).toInt()
            val (word, color) = when {
                changePct > 0 -> "FASTER" to colors.success
                changePct < 0 -> "SLOWER" to colors.danger
                else -> "UNCHANGED" to colors.onSurfaceVariant
            }
            Text(
                "${kotlin.math.abs(changePct)}% $word THAN LAST TEST (${"%.1f".format(previous)} MBPS)",
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
            Text("Wi-Fi link speed", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(
                text = state.linkSpeedMbps?.let { "$it Mbps" } ?: if (state.isOnWifi) "Unknown" else "Not on Wi-Fi",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        WifiLensDivider()
        Text(
            "Link speed is the rate your phone and router negotiated. It varies with signal and can differ from real throughput. " +
                "Download speed is what you actually get, and is also limited by your internet plan. " +
                "Test from the same spot before and after changing your router to compare.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = WifiLensSpacing.sm),
        )

        Spacer(Modifier.height(WifiLensSpacing.lg))

        when (test) {
            is SpeedTestState.Running -> {
                WifiLensWavyProgress(progress = { test.progress })
                Text("TESTING…", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            is SpeedTestState.Failed -> {
                Text(test.reason, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            }
            else -> {
                Unit
            }
        }
        WifiLensPrimaryButton(
            text = if (test is SpeedTestState.Finished) "Test again" else "Run speed test",
            onClick = { onAction(DiagnoseAction.RunSpeedTest) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun DiagnoseEmptyPreview() {
    WifiLensTheme {
        DiagnoseContent(state = DiagnoseState(), onAction = {})
    }
}
