package com.wickedcoder.wifilens.feature.analyze.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wickedcoder.wifilens.core.model.SecurityIssue
import com.wickedcoder.wifilens.feature.analyze.domain.Insight
import com.wickedcoder.wifilens.feature.analyze.domain.InsightSeverity

/** Title and plain-English fix for one health-check finding. */
@Composable
internal fun insightText(insight: Insight): Pair<String, String> = when (insight) {
    is Insight.Signal -> {
        when (insight.severity) {
            InsightSeverity.Good, InsightSeverity.Tip -> stringResource(R.string.analyze_insight_signal_good, insight.rssiDbm) to
                stringResource(R.string.analyze_insight_signal_good_body)
            InsightSeverity.Warning -> stringResource(R.string.analyze_insight_signal_fair, insight.rssiDbm) to
                stringResource(R.string.analyze_insight_signal_fair_body)
            InsightSeverity.Problem -> stringResource(R.string.analyze_insight_signal_weak, insight.rssiDbm) to
                stringResource(R.string.analyze_insight_signal_weak_body)
        }
    }

    is Insight.CrowdedChannel -> {
        val suggested = insight.suggestedChannel
        stringResource(R.string.analyze_insight_channel, insight.channel, insight.band) to when {
            suggested == null -> stringResource(R.string.analyze_insight_channel_none)
            insight.suggestedIsDfs -> stringResource(R.string.analyze_insight_channel_switch_dfs, suggested)
            else -> stringResource(R.string.analyze_insight_channel_switch, suggested)
        }
    }

    is Insight.BetterBand -> {
        stringResource(R.string.analyze_insight_band, insight.band) to
            stringResource(R.string.analyze_insight_band_body, insight.band, insight.rssiDbm)
    }

    is Insight.StrongerAccessPoint -> {
        stringResource(R.string.analyze_insight_stronger_ap) to
            stringResource(R.string.analyze_insight_stronger_ap_body, insight.bssidMasked, insight.rssiDbm - insight.currentRssiDbm)
    }

    is Insight.MeshDetected -> {
        stringResource(R.string.analyze_insight_mesh) to
            pluralStringResource(R.plurals.analyze_insight_mesh_body, insight.accessPointCount, insight.accessPointCount)
    }

    is Insight.Security -> {
        when (insight.issue) {
            SecurityIssue.Open -> stringResource(R.string.analyze_insight_open) to stringResource(R.string.analyze_insight_open_body)
            SecurityIssue.Wep -> stringResource(R.string.analyze_insight_wep) to stringResource(R.string.analyze_insight_wep_body)
            SecurityIssue.LegacyWpa -> stringResource(R.string.analyze_insight_wpa1) to stringResource(R.string.analyze_insight_wpa1_body)
            SecurityIssue.Tkip -> stringResource(R.string.analyze_insight_tkip) to stringResource(R.string.analyze_insight_tkip_body)
            SecurityIssue.Wpa3Transition -> stringResource(R.string.analyze_insight_transition) to
                stringResource(R.string.analyze_insight_transition_body)
        }
    }

    is Insight.Speed -> {
        when (insight.severity) {
            InsightSeverity.Good, InsightSeverity.Tip -> stringResource(R.string.analyze_insight_speed, insight.downloadMbps) to
                stringResource(R.string.analyze_insight_speed_good_body)
            InsightSeverity.Warning -> stringResource(R.string.analyze_insight_speed, insight.downloadMbps) to
                stringResource(R.string.analyze_insight_speed_slow_body)
            InsightSeverity.Problem -> stringResource(R.string.analyze_insight_speed, insight.downloadMbps) to
                stringResource(R.string.analyze_insight_speed_very_slow_body)
        }
    }
}
