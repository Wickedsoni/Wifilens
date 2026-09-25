package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/** Where the plan sits inside a canvas of the given (untransformed) size. */
class GridGeometry(canvasWidth: Float, canvasHeight: Float, val columns: Int, val rows: Int) {
    val cellSize = min(canvasWidth / columns, canvasHeight / rows)
    val gridWidth = cellSize * columns
    val gridHeight = cellSize * rows
    val originX = (canvasWidth - gridWidth) / 2f
    val originY = (canvasHeight - gridHeight) / 2f

    /** The cell under [world] (untransformed canvas px), or null if it's outside the grid. */
    fun cellAt(world: Offset): Vec2? {
        val x = ((world.x - originX) / cellSize).let { if (it < 0f) -1 else it.toInt() }
        val y = ((world.y - originY) / cellSize).let { if (it < 0f) -1 else it.toInt() }
        return if (x in 0 until columns && y in 0 until rows) Vec2(x, y) else null
    }
}

/** Everything the static layer draws, precomputed as a few batched Paths instead of per-cell calls. */
private class StaticLayer(
    val cellSize: Float,
    val gridLines: Path,
    val floorFills: Map<Int, Path>,
    val walls: Path,
    val doors: Path,
    val doorOrigins: List<Offset>,
    val drywallDots: List<Offset>,
    val wood: Path,
    val glass: Path,
    val brick: Path,
    val concrete: Path,
    val metal: Path,
)

private fun androidx.compose.ui.unit.Density.buildStaticLayer(
    size: Size,
    plan: GridPlan,
    roomIds: Set<Int>,
    dark: Boolean,
    tintFloors: Boolean,
): StaticLayer {
    val g = GridGeometry(size.width, size.height, plan.width, plan.height)
    val cell = g.cellSize
    val patternStep = 4.dp.toPx()
    // Below these sizes patterns/arcs are sub-pixel noise but dominate build time on big plans
    // (200x200 = 40k cells, 160k drywall dots). Skipping them keeps every repaint cheap.
    val detailed = cell >= 6.dp.toPx()

    val gridLines = Path().apply {
        for (i in 0..plan.width) {
            moveTo(g.originX + i * cell, g.originY)
            lineTo(g.originX + i * cell, g.originY + g.gridHeight)
        }
        for (j in 0..plan.height) {
            moveTo(g.originX, g.originY + j * cell)
            lineTo(g.originX + g.gridWidth, g.originY + j * cell)
        }
    }

    val floorFills = HashMap<Int, Path>()
    val walls = Path()
    val doors = Path()
    val doorOrigins = ArrayList<Offset>()
    val drywallDots = ArrayList<Offset>()
    val wood = Path()
    val glass = Path()
    val brick = Path()
    val concrete = Path()
    val metal = Path()

    for (y in 0 until plan.height) {
        // Consecutive wall tiles in a row become one rect instead of one each.
        var wallRunStart = -1

        fun flushWallRun(endX: Int) {
            if (wallRunStart < 0) return
            walls.addRect(Rect(g.originX + wallRunStart * cell, g.originY + y * cell, g.originX + endX * cell, g.originY + (y + 1) * cell))
            wallRunStart = -1
        }
        for (x in 0 until plan.width) {
            val left = g.originX + x * cell
            val top = g.originY + y * cell
            val rect = Rect(left, top, left + cell, top + cell)
            val type = plan.cellAt(x, y)
            if (type !is CellType.Empty) flushWallRun(x)
            when (type) {
                is CellType.Floor -> {
                    if (tintFloors && type.roomId in roomIds) {
                        floorFills.getOrPut(type.roomId) { Path() }.addRect(rect)
                    }
                } // unassigned floor stays bare

                is CellType.Empty -> {
                    if (wallRunStart < 0) wallRunStart = x
                    if (detailed) {
                        when (type.material) {
                            Material.Drywall -> {
                                val step = cell / 3f
                                for (row in 1..2) for (col in 1..2) drywallDots.add(Offset(left + col * step, top + row * step))
                            }

                            Material.Wood -> {
                                var yy = top + patternStep
                                while (yy < top + cell) {
                                    wood.moveTo(left, yy)
                                    wood.lineTo(left + cell, yy)
                                    yy += patternStep
                                }
                            }

                            Material.Glass -> {
                                glass.moveTo(left, top + cell)
                                glass.lineTo(left + cell, top)
                            }

                            Material.Brick -> {
                                brick.moveTo(left, top)
                                brick.lineTo(left + cell, top + cell)
                                brick.moveTo(left + cell, top)
                                brick.lineTo(left, top + cell)
                            }

                            Material.Concrete -> {
                                var step = patternStep
                                while (step < cell) {
                                    concrete.moveTo(left + step, top)
                                    concrete.lineTo(left + step, top + cell)
                                    concrete.moveTo(left, top + step)
                                    concrete.lineTo(left + cell, top + step)
                                    step += patternStep
                                }
                            }

                            Material.Metal -> {
                                metal.addRect(rect)
                            }
                        }
                    } else if (type.material == Material.Metal) {
                        metal.addRect(rect)
                    }
                }

                CellType.Door -> {
                    doors.addRect(rect)
                    if (detailed) doorOrigins.add(Offset(left, top))
                }
            }
        }
        flushWallRun(plan.width)
    }
    return StaticLayer(cell, gridLines, floorFills, walls, doors, doorOrigins, drywallDots, wood, glass, brick, concrete, metal)
}

private fun DrawScope.drawStaticLayer(
    layer: StaticLayer,
    dark: Boolean,
    wallFill: Color,
    doorFill: Color,
    doorArc: Color,
    pattern: Color,
    gridLine: Color,
) {
    // Floors: 60% fill + full-strength tile outline in the room's colour.
    layer.floorFills.forEach { (roomId, path) ->
        val color = roomColor(roomId, dark)
        drawPath(path, color.copy(alpha = 0.6f))
        drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
    }

    // Walls and doors are solid fills so they read as distinct from floor; material patterns go OVER them.
    drawPath(layer.walls, wallFill)
    drawPath(layer.doors, doorFill)

    if (layer.cellSize >= 3.dp.toPx()) drawPath(layer.gridLines, gridLine, style = Stroke(width = 0.5.dp.toPx()))

    if (layer.drywallDots.isNotEmpty()) {
        drawPoints(
            layer.drywallDots,
            PointMode.Points,
            pattern.copy(alpha = pattern.alpha * 0.6f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    val line = Stroke(width = 1f)
    drawPath(layer.wood, pattern, style = line)
    drawPath(layer.glass, pattern, style = line)
    drawPath(layer.brick, pattern, style = line)
    drawPath(layer.concrete, pattern, style = line)
    drawPath(layer.metal, pattern.copy(alpha = 0.8f))

    val strokeWidthPx = 1.5.dp.toPx()
    layer.doorOrigins.forEach { origin -> drawDoorTile(origin, layer.cellSize, doorArc, strokeWidthPx) }
}

/**
 * The 2D editor's cell rendering as a reusable, cached background: walls as filled rectangles with
 * their material pattern over them, doors, and hairline grid lines. Floors are tinted by room only
 * when [tintFloors] (the editor and ISO do; the Diagnose maps draw their own data on bare floor).
 * Rebuilt only when [plan]/[roomIds]/[colors]/[tintFloors] change, not per frame.
 */
fun Modifier.planBackdrop(
    plan: GridPlan,
    colors: NothingColors,
    roomIds: Set<Int> = emptySet(),
    tintFloors: Boolean = false,
): Modifier = drawWithCache {
    val layer = buildStaticLayer(size, plan, roomIds, colors.isDark, tintFloors)
    onDrawBehind {
        drawStaticLayer(
            layer,
            dark = colors.isDark,
            wallFill = colors.borderVisible.copy(alpha = 0.8f),
            doorFill = colors.surface,
            doorArc = colors.textPrimary,
            pattern = colors.textSecondary.copy(alpha = 0.6f),
            gridLine = colors.border,
        )
    }
}

private fun DrawScope.drawDoorTile(origin: Offset, cellSize: Float, color: Color, strokeWidthPx: Float) {
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(origin.x - cellSize, origin.y),
        size = Size(cellSize * 2, cellSize * 2),
        style = androidx.compose.ui.graphics.drawscope
            .Stroke(width = strokeWidthPx),
    )
}
