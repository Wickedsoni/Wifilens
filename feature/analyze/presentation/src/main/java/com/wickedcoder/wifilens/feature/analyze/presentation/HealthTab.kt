package com.wickedcoder.wifilens.feature.analyze.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wickedcoder.wifilens.core.designsystem.WifiLensCard
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensEmptyState
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.WifiLensWavyProgress
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.core.designsystem.warning
import com.wickedcoder.wifilens.feature.analyze.domain.ChannelPlan
import com.wickedcoder.wifilens.feature.analyze.domain.ConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.Insight
import com.wickedcoder.wifilens.feature.analyze.domain.InsightSeverity

@Composable
internal fun HealthTab(state: InsightsState, onRunCheck: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WifiLensSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.md),
    ) {
        if (state.connected == null) {
            WifiLensEmptyState(
                title = stringResource(R.string.analyze_health_disconnected_title),
                description = stringResource(R.string.analyze_health_disconnected_body),
            )
        } else {
            VerdictCard(state, onRunCheck)
            state.insights.forEach { insight ->
                InsightRow(insight)
                WifiLensDivider()
            }
        }
        // The planner needs only the scan, so it's useful before joining a network too.
        if (state.channelPlan.isNotEmpty()) ChannelPlanCard(state.channelPlan)
    }
}

@Composable
private fun VerdictCard(state: InsightsState, onRunCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val toFix = state.insights.count { it.severity >= InsightSeverity.Warning }
    WifiLensCard {
        Column(modifier = Modifier.padding(WifiLensSpacing.md), verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            Text(
                text = when {
                    toFix > 0 -> pluralStringResource(R.plurals.analyze_health_to_fix, toFix, toFix)
                    state.overall == InsightSeverity.Tip -> stringResource(R.string.analyze_health_good_with_tips)
                    else -> stringResource(R.string.analyze_health_good)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.forSeverity(state.overall),
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.analyze_health_intro), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            when (val check = state.check) {
                is HealthCheckState.Running -> {
                    WifiLensWavyProgress(progress = { check.progress })
                    Text(
                        stringResource(R.string.analyze_health_testing),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                HealthCheckState.Failed -> {
                    Text(
                        stringResource(R.string.analyze_health_speed_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                    WifiLensPrimaryButton(
                        stringResource(R.string.analyze_health_run),
                        onClick = onRunCheck,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is HealthCheckState.Done, HealthCheckState.Idle -> {
                    WifiLensPrimaryButton(
                        text = stringResource(
                            if (check is HealthCheckState.Done) R.string.analyze_health_run_again else R.string.analyze_health_run,
                        ),
                        onClick = onRunCheck,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: Insight) {
    val colors = MaterialTheme.colorScheme
    val (title, body) = insightText(insight)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
    ) {
        Box(
            modifier = Modifier.padding(top = 6.dp).size(10.dp).background(colors.forSeverity(insight.severity), CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.xs)) {
            Text(
                text = stringResource(R.string.analyze_insight_title, stringResource(insight.severity.label), title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelPlanCard(plan: List<ChannelPlan>) {
    val colors = MaterialTheme.colorScheme
    WifiLensCard {
        Column(modifier = Modifier.padding(WifiLensSpacing.md), verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            WifiLensLabel(stringResource(R.string.analyze_planner_title))
            Text(
                stringResource(R.string.analyze_planner_intro),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            plan.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.analyze_band_ghz, band.band),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                    )
                    Text(
                        text = when {
                            band.isCurrent -> stringResource(R.string.analyze_planner_current, band.channel)
                            band.isDfs -> stringResource(R.string.analyze_planner_channel_dfs, band.channel, band.congestionScore)
                            else -> stringResource(R.string.analyze_planner_channel, band.channel, band.congestionScore)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (band.isCurrent) colors.success else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val InsightSeverity.label: Int
    get() = when (this) {
        InsightSeverity.Good -> R.string.analyze_severity_good
        InsightSeverity.Tip -> R.string.analyze_severity_tip
        InsightSeverity.Warning -> R.string.analyze_severity_warning
        InsightSeverity.Problem -> R.string.analyze_severity_problem
    }

private fun ColorScheme.forSeverity(severity: InsightSeverity): Color = when (severity) {
    InsightSeverity.Good -> success
    InsightSeverity.Tip -> primary
    InsightSeverity.Warning -> warning
    InsightSeverity.Problem -> danger
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun HealthTabPreview() {
    WifiLensTheme {
        HealthTab(
            state = InsightsState(
                connected = ConnectedNetwork(ssid = "Home", rssiDbm = -72, channel = 6, band = "2.4"),
                insights = listOf(
                    Insight.Signal(-72, InsightSeverity.Warning),
                    Insight.BetterBand("5", -64),
                    Insight.Security(com.wickedcoder.wifilens.core.model.SecurityIssue.Wpa3Transition),
                ),
                overall = InsightSeverity.Warning,
                channelPlan = listOf(ChannelPlan("2.4", 11, 12, isDfs = false, isCurrent = false), ChannelPlan("5", 36, 0, false, false)),
            ),
            onRunCheck = {},
        )
    }
}
