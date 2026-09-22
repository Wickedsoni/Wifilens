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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingEmptyState
import com.wickedcoder.wifilens.core.designsystem.NothingLabel
import com.wickedcoder.wifilens.core.designsystem.NothingPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.NothingSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.StatusDot
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiagnoseScreen(
    viewModel: DiagnoseViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
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
    val colors = WifiLensTheme.colors

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        Column(modifier = Modifier.padding(NothingSpacing.md)) {
            NothingSegmentedControl(
                items = listOf("Coverage", "Best spot"),
                selectedIndex = if (state.tab is DiagnoseTab.Coverage) 0 else 1,
                onSelect = { onAction(if (it == 0) DiagnoseAction.TabCoverage else DiagnoseAction.TabBestSpot) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.padding(top = NothingSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
            ) {
                Text("[PREDICTED]", style = NothingType.caption, color = colors.textDisabled)
                Text(
                    "Estimate from your plan, not a measurement.",
                    style = NothingType.caption,
                    color = colors.textDisabled,
                )
            }
        }

        if (state.plan == null || state.routerPos == null) {
            NothingEmptyState(
                title = "Nothing to diagnose yet",
                description = "Create a floor plan with a router and at least one device pin first.",
            )
            return
        }

        when (state.tab) {
            DiagnoseTab.Coverage -> CoverageTab(state)
            DiagnoseTab.BestSpot -> BestSpotTab(state, onAction)
        }
    }
}

@Composable
private fun CoverageTab(state: DiagnoseState) {
    val colors = WifiLensTheme.colors
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
            style = NothingType.caption,
            color = colors.textDisabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NothingSpacing.sm).padding(top = NothingSpacing.sm),
        )
        // Coverage is recomputed automatically whenever the plan, pins or model settings change, so
        // there is nothing to trigger — say so instead of offering a button that would do nothing.
        Text(
            "COVERAGE IS COMPUTED FROM YOUR FLOOR PLAN",
            style = NothingType.caption,
            color = colors.textDisabled,
            modifier = Modifier.fillMaxWidth().padding(NothingSpacing.sm),
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(0.55f), contentPadding = androidx.compose.foundation.layout.PaddingValues(NothingSpacing.md)) {
            item {
                state.worstDevice?.let { (pin, rssi) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = NothingSpacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${rssi.toInt()}", style = NothingType.displayMd, color = rssiColor(rssi, colors))
                            Text(" DBM", style = NothingType.label, color = colors.textSecondary)
                            Spacer(Modifier.width(NothingSpacing.sm))
                            Text("[PREDICTED]", style = NothingType.caption, color = colors.textDisabled)
                        }
                        NothingLabel("Weakest device: ${pin.name}", modifier = Modifier.padding(top = NothingSpacing.xs))
                    }
                }
            }

            items(state.roomSummaries) { room ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedRoomId = if (selectedRoomId == room.roomId) null else room.roomId }
                        .padding(vertical = NothingSpacing.sm),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(room.name, style = NothingType.body, color = colors.textPrimary)
                        Text("${room.avgRssi.toInt()} dBm", style = NothingType.body, color = rssiColor(room.avgRssi, colors))
                    }
                    NothingDivider(modifier = Modifier.padding(top = NothingSpacing.sm))
                }
            }

            if (state.findings.isNotEmpty()) {
                item { Spacer(Modifier.height(NothingSpacing.lg)) }
                items(state.findings) { finding ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = NothingSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
                    ) {
                        StatusDot(color = if (finding.severity == Severity.Poor) colors.accent else colors.warning)
                        Text(finding.description, style = NothingType.bodySmall, color = colors.textPrimary)
                    }
                    NothingDivider()
                }
            }
        }
    }
}

@Composable
private fun BestSpotTab(state: DiagnoseState, onAction: (DiagnoseAction) -> Unit) {
    val colors = WifiLensTheme.colors

    when (val optimizer = state.optimizerState) {
        OptimizerState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NothingPrimaryButton(text = "Find best router spot", onClick = { onAction(DiagnoseAction.RunOptimizer) })
        }

        is OptimizerState.Running -> {
            val total = state.plan?.let { it.width * it.height } ?: 0
            val evaluated = (optimizer.progress * total).toInt()
            Column(modifier = Modifier.fillMaxSize().padding(NothingSpacing.md), verticalArrangement = Arrangement.Center) {
                SegmentedProgressBar(progress = optimizer.progress)
                Text(
                    "EVALUATING $evaluated / $total TILES",
                    style = NothingType.label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = NothingSpacing.sm),
                )
            }
        }

        OptimizerState.Complete -> Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(0.5f)) {
                BestSpotMapCanvas(
                    plan = state.plan!!,
                    tileScores = state.tileScores,
                    bestTile = state.bestTile,
                    routerPos = state.routerPos,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().weight(0.5f).padding(NothingSpacing.md)) {
                val gain = state.bestTileGainDb
                Text(
                    text = if (gain != null) "+${"%.1f".format(gain)} dB" else "—",
                    style = NothingType.displayLg,
                    color = colors.success,
                )
                val worstNow = state.worstDevice?.second
                val worstBest = state.bestTile?.let { state.tileScores[it] }
                if (worstNow != null && worstBest != null) {
                    Text(
                        "WORST DEVICE ${worstNow.toInt()} → ${worstBest.toInt()} (PREDICTED)",
                        style = NothingType.label,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = NothingSpacing.xs),
                    )
                }
                Spacer(Modifier.height(NothingSpacing.lg))
                state.bestTile?.let { tile ->
                    NothingPrimaryButton(
                        text = "Move router here",
                        onClick = { onAction(DiagnoseAction.MoveRouter(tile)) },
                    )
                }
            }
        }

        OptimizerState.AlreadyOptimal -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Current spot is already the best tile.", style = NothingType.body, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SegmentedProgressBar(progress: Float) {
    val colors = WifiLensTheme.colors
    val segments = 24
    val filled = (progress * segments).toInt()
    Row(modifier = Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(segments) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (index < filled) colors.textDisplay else colors.border),
            )
        }
    }
}

private fun rssiColor(rssi: Float, colors: com.wickedcoder.wifilens.core.designsystem.NothingColors) = when {
    rssi >= -67f -> colors.success
    rssi >= -75f -> colors.warning
    else -> colors.accent
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun DiagnoseEmptyPreview() {
    WifiLensTheme {
        DiagnoseContent(state = DiagnoseState(), onAction = {})
    }
}
