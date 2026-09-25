package com.wickedcoder.wifilens.feature.diagnose.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.feature.map.domain.DevicePin
import com.wickedcoder.wifilens.feature.map.presentation.planBackdrop
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import kotlin.math.min

private const val GOOD_RSSI = -67f
private const val FAIR_RSSI = -75f

private fun rssiColor(rssi: Float, colors: com.wickedcoder.wifilens.core.designsystem.NothingColors): Color = when {
    rssi >= GOOD_RSSI -> colors.success
    rssi >= FAIR_RSSI -> colors.warning
    else -> colors.accent
}

private fun rssiDotRadius(rssi: Float, cellSizePx: Float): Float = when {
    rssi >= GOOD_RSSI -> cellSizePx * 0.32f
    rssi >= FAIR_RSSI -> cellSizePx * 0.22f
    else -> cellSizePx * 0.12f
}

private data class GridMetrics(val cellSizePx: Float, val originX: Float, val originY: Float)

private fun computeMetrics(plan: GridPlan, canvasWidth: Float, canvasHeight: Float): GridMetrics {
    val cellSizePx = min(canvasWidth / plan.width, canvasHeight / plan.height)
    val originX = (canvasWidth - cellSizePx * plan.width) / 2f
    val originY = (canvasHeight - cellSizePx * plan.height) / 2f
    return GridMetrics(cellSizePx, originX, originY)
}

@Composable
fun CoverageMapCanvas(
    plan: GridPlan,
    coverage: List<TileCoverage>,
    routerPos: Vec2?,
    devicePins: List<DevicePin>,
    selectedRoomId: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val coverageByPos = remember(coverage) { coverage.associateBy { it.pos } }

    // Walls, doors and grid lines: the same rendering as the 2D editor, cached behind the data drawn below.
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Predicted Wi-Fi coverage map. Green is good signal, amber fair, red poor." }
            .planBackdrop(plan, colors),
    ) {
        val metrics = computeMetrics(plan, size.width, size.height)
        val (cellSizePx, originX, originY) = metrics

        for (y in 0 until plan.height) {
            for (x in 0 until plan.width) {
                val cellOrigin = Offset(originX + x * cellSizePx, originY + y * cellSizePx)
                when (val cell = plan.cellAt(x, y)) {
                    is CellType.Empty -> Unit // walls come from planBackdrop
                    is CellType.Floor -> {
                        val highlighted = selectedRoomId == null || selectedRoomId == cell.roomId
                        val rssi = coverageByPos[Vec2(x, y)]?.rssi
                        if (rssi != null) {
                            val alpha = if (highlighted) 1f else 0.25f
                            drawCircle(
                                color = rssiColor(rssi, colors).copy(alpha = alpha),
                                radius = rssiDotRadius(rssi, cellSizePx),
                                center = Offset(cellOrigin.x + cellSizePx / 2f, cellOrigin.y + cellSizePx / 2f),
                            )
                        }
                    }

                    CellType.Door -> Unit
                }
            }
        }

        routerPos?.let { pos ->
            val center = Offset(originX + (pos.x + 0.5f) * cellSizePx, originY + (pos.y + 0.5f) * cellSizePx)
            drawCircle(color = colors.textDisplay, radius = cellSizePx * 0.18f, center = center)
        }

        devicePins.forEach { pin ->
            val center = Offset(originX + (pin.pos.x + 0.5f) * cellSizePx, originY + (pin.pos.y + 0.5f) * cellSizePx)
            val rssi = coverageByPos[pin.pos]?.rssi
            val label = if (rssi != null) "${rssi.toInt()} dBm" else pin.name
            val layout = textMeasurer.measure(label, TextStyle(fontSize = NothingType.caption.fontSize, color = colors.textDisplay))
            drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y + cellSizePx * 0.2f))
        }
    }
}

@Composable
fun BestSpotMapCanvas(
    plan: GridPlan,
    tileScores: Map<Vec2, Float>,
    bestTile: Vec2?,
    routerPos: Vec2?,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    val minScore = tileScores.values.minOrNull() ?: -100f
    val maxScore = tileScores.values.maxOrNull() ?: -40f
    val range = (maxScore - minScore).coerceAtLeast(1f)

    // Walls, doors and grid lines: the same rendering as the 2D editor, cached behind the data drawn below.
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Best router spot map. Brighter tiles score better for your device pins." }
            .planBackdrop(plan, colors),
    ) {
        val metrics = computeMetrics(plan, size.width, size.height)
        val (cellSizePx, originX, originY) = metrics

        for (y in 0 until plan.height) {
            for (x in 0 until plan.width) {
                val cellOrigin = Offset(originX + x * cellSizePx, originY + y * cellSizePx)
                when (val cell = plan.cellAt(x, y)) {
                    is CellType.Empty -> Unit // walls come from planBackdrop
                    is CellType.Floor -> {
                        val score = tileScores[Vec2(x, y)]
                        if (score != null) {
                            val alpha = ((score - minScore) / range).coerceIn(0.08f, 1f)
                            drawRect(
                                color = colors.textDisplay.copy(alpha = alpha * 0.5f),
                                topLeft = cellOrigin,
                                size = Size(cellSizePx, cellSizePx),
                            )
                        }
                    }

                    CellType.Door -> Unit
                }
            }
        }

        bestTile?.let { pos ->
            val topLeft = Offset(originX + pos.x * cellSizePx, originY + pos.y * cellSizePx)
            val strokeWidth = 1.5.dp.toPx()
            val bracket = cellSizePx * 0.3f
            // Four corner brackets rather than a full rect — reads as a "target" marker.
            listOf(
                topLeft to Offset(bracket, 0f), // top-left horizontal
                topLeft to Offset(0f, bracket), // top-left vertical
                Offset(topLeft.x + cellSizePx, topLeft.y) to Offset(-bracket, 0f),
                Offset(topLeft.x + cellSizePx, topLeft.y) to Offset(0f, bracket),
                Offset(topLeft.x, topLeft.y + cellSizePx) to Offset(bracket, 0f),
                Offset(topLeft.x, topLeft.y + cellSizePx) to Offset(0f, -bracket),
                Offset(topLeft.x + cellSizePx, topLeft.y + cellSizePx) to Offset(-bracket, 0f),
                Offset(topLeft.x + cellSizePx, topLeft.y + cellSizePx) to Offset(0f, -bracket),
            ).forEach { (corner, delta) ->
                drawLine(colors.textDisplay, corner, Offset(corner.x + delta.x, corner.y + delta.y), strokeWidth = strokeWidth)
            }
        }

        routerPos?.let { pos ->
            val center = Offset(originX + (pos.x + 0.5f) * cellSizePx, originY + (pos.y + 0.5f) * cellSizePx)
            drawCircle(
                color = colors.textSecondary,
                radius = cellSizePx * 0.22f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}
