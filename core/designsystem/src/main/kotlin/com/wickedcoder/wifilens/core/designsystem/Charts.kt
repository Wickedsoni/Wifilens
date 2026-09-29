package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Buckets drawn by [WifiLensLineChart]; about one per 3 dp on a phone-width chart. */
private const val LINE_CHART_BUCKETS = 120

/**
 * A line chart on fixed axes ([xRange], [yRange]) so charts of different data stay comparable. [guides] are
 * horizontal reference lines (e.g. the good/fair signal thresholds) drawn in [guideColor]. Points are downsampled
 * before drawing. [colorFor] picks the line colour from the latest value.
 */
@Composable
fun WifiLensLineChart(
    points: List<ChartPoint>,
    xRange: ClosedFloatingPointRange<Float>,
    yRange: ClosedFloatingPointRange<Float>,
    description: String,
    modifier: Modifier = Modifier,
    guides: List<Float> = emptyList(),
    guideColor: (Float) -> Color = { Color.Unspecified },
    colorFor: (Float) -> Color = { Color.Unspecified },
) {
    val colors = MaterialTheme.colorScheme
    val drawn = remember(points) { downsampleMinMax(points, LINE_CHART_BUCKETS) }
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        fun xOf(x: Float) = size.width * ((x - xRange.start) / (xRange.endInclusive - xRange.start)).coerceIn(0f, 1f)

        fun yOf(y: Float) = size.height * (1f - ((y - yRange.start) / (yRange.endInclusive - yRange.start)).coerceIn(0f, 1f))

        drawLine(colors.outlineVariant, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        guides.forEach { g ->
            val color = guideColor(g).orIfUnspecified(colors.outlineVariant).copy(alpha = 0.5f)
            drawLine(color, Offset(0f, yOf(g)), Offset(size.width, yOf(g)), 1.dp.toPx())
        }
        if (drawn.isEmpty()) return@Canvas
        val lineColor = colorFor(drawn.last().y).orIfUnspecified(colors.primary)
        if (drawn.size == 1) {
            drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(xOf(drawn[0].x), yOf(drawn[0].y)))
            return@Canvas
        }
        val path = Path().apply {
            drawn.forEachIndexed { i, p -> if (i == 0) moveTo(xOf(p.x), yOf(p.y)) else lineTo(xOf(p.x), yOf(p.y)) }
        }
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Vertical bars, one per value, scaled to [maxValue] (or the largest value). A null value is a gap (no data), drawn
 * as a faint stub so "nothing recorded" isn't mistaken for zero.
 */
@Composable
fun WifiLensBarChart(
    values: List<Float?>,
    description: String,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    barColor: (index: Int, value: Float) -> Color = { _, _ -> Color.Unspecified },
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val top = maxValue ?: values.filterNotNull().maxOrNull()?.takeIf { it > 0f } ?: 1f
        val slot = size.width / values.size
        val gap = slot * 0.2f
        val radius = CornerRadius(2.dp.toPx())
        values.forEachIndexed { i, v ->
            val left = i * slot + gap / 2
            if (v == null) {
                val stub = 2.dp.toPx()
                drawRoundRect(colors.outlineVariant, Offset(left, size.height - stub), Size(slot - gap, stub), radius)
            } else {
                val h = (size.height * (v / top)).coerceIn(2.dp.toPx(), size.height)
                drawRoundRect(barColor(i, v).orIfUnspecified(colors.primary), Offset(left, size.height - h), Size(slot - gap, h), radius)
            }
        }
    }
}

private fun Color.orIfUnspecified(fallback: Color): Color = if (this == Color.Unspecified) fallback else this
