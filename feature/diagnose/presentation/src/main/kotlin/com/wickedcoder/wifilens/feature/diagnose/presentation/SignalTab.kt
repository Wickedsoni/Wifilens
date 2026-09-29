package com.wickedcoder.wifilens.feature.diagnose.presentation

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.FAIR_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.GOOD_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.signalColor

/** The gauge spans this RSSI range; readings outside are pinned to the ends. */
private const val GAUGE_MIN_DBM = -90f
private const val GAUGE_MAX_DBM = -30f

/** A 240° arc opening at the bottom, like a car's speedometer. */
private const val GAUGE_START_DEG = 150f
private const val GAUGE_SWEEP_DEG = 240f

private enum class SignalTier(
    @StringRes val label: Int,
) {
    Good(R.string.diagnose_signal_good),
    Fair(R.string.diagnose_signal_fair),
    Poor(R.string.diagnose_signal_poor),
}

private fun tierOf(rssi: Int): SignalTier = when {
    rssi >= GOOD_RSSI_DBM -> SignalTier.Good
    rssi >= FAIR_RSSI_DBM -> SignalTier.Fair
    else -> SignalTier.Poor
}

@Composable
internal fun SignalTab(viewModel: SignalMeterViewModel = hiltViewModel()) {
    val meter by viewModel.state.collectAsStateWithLifecycle()
    SignalMeter(meter)
}

@Composable
internal fun SignalMeter(meter: SignalMeterState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val tier = meter.rssi?.let(::tierOf)
    // A tick when the quality tier changes (walking into a weaker room), not on every 1 dB wobble.
    LaunchedEffect(tier) {
        if (tier != null && meter.tierHaptics) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WifiLensSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.md),
    ) {
        Gauge(rssi = meter.rssi, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
        Text(
            text = tier?.let { stringResource(it.label) } ?: stringResource(R.string.diagnose_signal_disconnected),
            style = MaterialTheme.typography.titleMedium,
            color = meter.rssi?.let { colors.signalColor(it.toFloat()) } ?: colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.lg)) {
            Stat(stringResource(R.string.diagnose_signal_best), meter.best?.let { stringResource(R.string.diagnose_signal_dbm, it) })
            Stat(stringResource(R.string.diagnose_signal_worst), meter.worst?.let { stringResource(R.string.diagnose_signal_dbm, it) })
            Stat(stringResource(R.string.diagnose_signal_band), meter.band?.let { stringResource(R.string.diagnose_signal_ghz, it) })
            Stat(
                stringResource(R.string.diagnose_signal_link),
                meter.linkSpeedMbps?.let { stringResource(R.string.diagnose_signal_mbps, it) },
            )
        }
        Trace(history = meter.history, modifier = Modifier.fillMaxWidth().height(96.dp))
        Text(
            stringResource(R.string.diagnose_signal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Gauge(rssi: Int?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val target = ((rssi ?: GAUGE_MIN_DBM.toInt()) - GAUGE_MIN_DBM) / (GAUGE_MAX_DBM - GAUGE_MIN_DBM)
    // Spring, not tween: the needle overshoots a little and settles, and a new reading mid-flight just retargets it.
    val fraction by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
    )
    val valueText = rssi?.let { stringResource(R.string.diagnose_signal_dbm, it) } ?: stringResource(R.string.diagnose_signal_none)
    val arcColor = rssi?.let { colors.signalColor(it.toFloat()) } ?: colors.outlineVariant

    Box(modifier = modifier.aspectRatio(1.4f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = valueText }) {
            val stroke = 18.dp.toPx()
            val diameter = minOf(size.width, size.height * 1.4f) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = colors.surfaceContainerHighest,
                startAngle = GAUGE_START_DEG,
                sweepAngle = GAUGE_SWEEP_DEG,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (rssi != null) {
                drawArc(
                    color = arcColor,
                    startAngle = GAUGE_START_DEG,
                    sweepAngle = GAUGE_SWEEP_DEG * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.displaySmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The last minute of readings, on the gauge's scale, with the good/fair thresholds as guide lines. */
@Composable
private fun Trace(history: List<Int>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.diagnose_signal_trace_description)
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        fun yOf(dbm: Float) = size.height * (1f - ((dbm - GAUGE_MIN_DBM) / (GAUGE_MAX_DBM - GAUGE_MIN_DBM)).coerceIn(0f, 1f))
        val guide = 1.dp.toPx()
        listOf(GOOD_RSSI_DBM, FAIR_RSSI_DBM).forEach { dbm ->
            drawLine(colors.signalColor(dbm).copy(alpha = 0.5f), Offset(0f, yOf(dbm)), Offset(size.width, yOf(dbm)), guide)
        }
        if (history.size < 2) return@Canvas
        val step = size.width / (METER_HISTORY_SIZE - 1)
        val startX = size.width - step * (history.size - 1) // newest reading at the right edge
        val path = Path().apply {
            history.forEachIndexed { i, dbm ->
                val point = Offset(startX + i * step, yOf(dbm.toFloat()))
                if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        drawPath(path, colors.signalColor(history.last().toFloat()), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun Stat(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: stringResource(R.string.diagnose_signal_none), style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun SignalMeterPreview() {
    WifiLensTheme {
        val history = remember { List(40) { -60 - (it % 9) } }
        SignalMeter(SignalMeterState(rssi = -61, band = "5", linkSpeedMbps = 866, history = history, best = -52, worst = -71))
    }
}
