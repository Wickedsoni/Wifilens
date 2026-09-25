package com.wickedcoder.wifilens.feature.map.presentation

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
import com.wickedcoder.wifilens.core.designsystem.NothingColors
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.feature.map.domain.DevicePin
import com.wickedcoder.wifilens.feature.map.domain.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

private const val MIN_SCALE = 0.5f
/** How long a zoomed tap is held back to see whether a second tap makes it a reset double-tap. */
private const val DOUBLE_TAP_WINDOW_MS = 200L
private const val MAX_SCALE = 4f
private val MIN_LABEL_WIDTH = 48.dp
private val MIN_LABEL_HEIGHT = 24.dp

/** Where the plan sits inside a canvas of the given (untransformed) size. */
private class GridGeometry(canvasWidth: Float, canvasHeight: Float, val columns: Int, val rows: Int) {
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

/**
 * The 2D map editor. One finger paints (tap = one cell, drag = a stroke); two fingers pinch-zoom
 * and pan; double-tapping the same spot while zoomed resets the view.
 *
 * Gestures live in ONE pointerInput rather than a tap + drag + transform trio, because they
 * compete: a drag detector consumes its pointer's movement, which cancels a sibling transform
 * detector, so two-finger pinches never got through. Here the number of fingers down picks the
 * mode. Touch positions are mapped back through the inverse of the view transform before being
 * turned into cells, so painting still lands on the right tile when zoomed or panned.
 *
 * Zoom/pan state is private to this composable and only read in the layer/draw phases, so a
 * pinch moves a GPU layer and does not recompose or redraw the map. The static layer (cells,
 * patterns, grid) is cached and only rebuilt when the plan/rooms/theme change.
 */
@Composable
fun MapCanvas(
    plan: GridPlan,
    rooms: List<Room>,
    routerPos: Vec2?,
    devicePins: List<DevicePin>,
    /** [isDrag] is false for a stationary tap, true for the start/continuation of a drag. */
    onCellTouched: (x: Int, y: Int, isDrag: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Already ANDed with the master haptics switch. */
    paintHaptics: Boolean = true,
) {
    val colors = WifiLensTheme.colors
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    // The gesture coroutine outlives recompositions; without rememberUpdatedState it would keep
    // calling the lambda / reading the plan from the first composition (a tool switch used to be
    // ignored for exactly this reason).
    val currentOnCellTouched by rememberUpdatedState(onCellTouched)
    val currentPaintHaptics by rememberUpdatedState(paintHaptics)
    val currentPlan by rememberUpdatedState(plan)

    var viewScale by remember(plan.width, plan.height) { mutableFloatStateOf(1f) }
    var viewOffset by remember(plan.width, plan.height) { mutableStateOf(Offset.Zero) }

    val roomIds = remember(rooms) { rooms.map { it.id }.toSet() }
    val roomBounds = remember(plan, rooms) { computeRoomBounds(plan).filterKeys { it in roomIds } }
    val labelledRooms = remember(roomBounds) { resolveLabelOverlaps(roomBounds) }
    val roomLabels = remember(rooms, colors) {
        rooms.associate { room ->
            room.id to textMeasurer.measure(
                text = room.name.uppercase(),
                style = TextStyle(fontSize = NothingType.label.fontSize, color = colors.textPrimary, textAlign = TextAlign.Center),
            )
        }
    }
    val pinLabels = remember(devicePins, colors) {
        devicePins.map { pin ->
            textMeasurer.measure(
                text = pin.name,
                style = TextStyle(fontSize = NothingType.caption.fontSize, color = colors.textDisplay),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics { contentDescription = "Floor plan editor, ${plan.width} by ${plan.height} tiles. Tap or drag to paint." }
            .pointerInput(Unit) {
                coroutineScope {
                    val slop = viewConfiguration.touchSlop
                    val minVisiblePx = 48.dp.toPx()

                    // A tap made while zoomed is held back for DOUBLE_TAP_WINDOW_MS in case it is the
                    // first half of a reset double-tap (which must not paint). At 1x there is nothing
                    // to reset, so taps go straight through with no added latency.
                    var pendingTap: PendingTap? = null

                    fun flushPendingTap() {
                        val pending = pendingTap ?: return
                        pendingTap = null
                        pending.job.cancel()
                        currentOnCellTouched(pending.cell.x, pending.cell.y, false)
                    }

                    fun geometry() = GridGeometry(size.width.toFloat(), size.height.toFloat(), currentPlan.width, currentPlan.height)

                    /** Keeps at least [minVisiblePx] of the grid on screen so it can't be flung away. */
                    fun clampedOffset(offset: Offset, scale: Float): Offset {
                        val g = geometry()
                        val minX = minVisiblePx - scale * (g.originX + g.gridWidth)
                        val maxX = size.width - minVisiblePx - scale * g.originX
                        val minY = minVisiblePx - scale * (g.originY + g.gridHeight)
                        val maxY = size.height - minVisiblePx - scale * g.originY
                        return Offset(offset.x.coerceIn(minX, maxOf(minX, maxX)), offset.y.coerceIn(minY, maxOf(minY, maxY)))
                    }

                    fun toCell(screen: Offset): Vec2? = geometry().cellAt((screen - viewOffset) / viewScale)

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val waiting = pendingTap
                        if (waiting != null && (down.position - waiting.position).getDistance() > slop * 2) {
                            // A touch somewhere else: the held-back tap was a plain tap after all.
                            flushPendingTap()
                        }

                        var dragging = false
                        var transforming = false
                        var lastCell: Vec2? = null

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break // every finger is up

                            if (pressed.size >= 2 || transforming) {
                                // Once a second finger has landed, the rest of this gesture is a transform,
                                // even if it drops back to one finger — that finger must not start painting.
                                if (!transforming) flushPendingTap()
                                transforming = true
                                if (pressed.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        val newScale = (viewScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                        val scaleChange = newScale / viewScale
                                        // Zoom about the fingers' centroid so the point under them stays put.
                                        viewOffset = clampedOffset((viewOffset - centroid) * scaleChange + centroid + pan, newScale)
                                        viewScale = newScale
                                    }
                                }
                                event.changes.forEach(PointerInputChange::consume)
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                            if (!dragging) {
                                if ((change.position - down.position).getDistance() <= slop) continue
                                dragging = true
                                flushPendingTap()
                            }

                            val cell = toCell(change.position)
                            if (cell != null && cell != lastCell) {
                                if (lastCell != null && currentPaintHaptics) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                lastCell = cell
                                currentOnCellTouched(cell.x, cell.y, true)
                            }
                            change.consume()
                        }

                        if (!dragging && !transforming) {
                            val firstHalf = pendingTap
                            if (firstHalf != null) {
                                // Second half of a double-tap at the same spot: reset the view. Neither tap paints.
                                firstHalf.job.cancel()
                                pendingTap = null
                                viewScale = 1f
                                viewOffset = Offset.Zero
                            } else {
                                val cell = toCell(down.position)
                                if (cell != null) {
                                    val zoomedOrPanned = viewScale != 1f || viewOffset != Offset.Zero
                                    if (zoomedOrPanned) {
                                        val job = launch {
                                            delay(DOUBLE_TAP_WINDOW_MS)
                                            val timedOut = pendingTap
                                            pendingTap = null
                                            if (timedOut != null) currentOnCellTouched(timedOut.cell.x, timedOut.cell.y, false)
                                        }
                                        pendingTap = PendingTap(cell, down.position, job)
                                    } else {
                                        currentOnCellTouched(cell.x, cell.y, false)
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = viewScale
                    scaleY = viewScale
                    translationX = viewOffset.x
                    translationY = viewOffset.y
                },
        ) {
            // Static layer: cached, rebuilt only when plan/rooms/theme change.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .planBackdrop(plan, colors, roomIds, tintFloors = true),
            ) {}

            // Dynamic layer: labels + pins. Cheap (a handful of items); reads viewScale so the
            // "is this room big enough on screen to label" test follows the zoom.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val g = GridGeometry(size.width, size.height, plan.width, plan.height)
                val minLabelWidthPx = MIN_LABEL_WIDTH.toPx()
                val minLabelHeightPx = MIN_LABEL_HEIGHT.toPx()
                val onScreenCell = g.cellSize * viewScale
                labelledRooms.forEach { (roomId, bounds) ->
                    val layout: TextLayoutResult = roomLabels[roomId] ?: return@forEach
                    // Skip labels for rooms too small on screen to hold one: the bounding box must be
                    // at least 48dp x 24dp, and the room's area-equivalent side must clear 48dp too so
                    // a long thin sliver doesn't pass on its length alone.
                    val boxWidthPx = (bounds.maxX - bounds.minX + 1) * onScreenCell
                    val boxHeightPx = (bounds.maxY - bounds.minY + 1) * onScreenCell
                    if (boxWidthPx < minLabelWidthPx || boxHeightPx < minLabelHeightPx) return@forEach
                    if (onScreenCell * sqrt(bounds.tileCount.toFloat()) < minLabelWidthPx) return@forEach

                    val left = g.originX + bounds.minX * g.cellSize
                    val top = g.originY + bounds.minY * g.cellSize
                    val right = g.originX + (bounds.maxX + 1) * g.cellSize
                    val bottom = g.originY + (bounds.maxY + 1) * g.cellSize
                    val center = Offset((left + right) / 2f, (top + bottom) / 2f)
                    // Clip to the room's own box so a long name never spills onto a neighbour.
                    clipRect(left, top, right, bottom) {
                        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
                    }
                }

                routerPos?.let { pos ->
                    val center = Offset(g.originX + (pos.x + 0.5f) * g.cellSize, g.originY + (pos.y + 0.5f) * g.cellSize)
                    drawCircle(color = colors.textDisplay, radius = g.cellSize * 0.18f, center = center)
                    drawCircle(
                        color = colors.textDisplay,
                        radius = g.cellSize * 0.32f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }

                devicePins.forEachIndexed { index, pin ->
                    val center = Offset(g.originX + (pin.pos.x + 0.5f) * g.cellSize, g.originY + (pin.pos.y + 0.5f) * g.cellSize)
                    drawCircle(
                        color = colors.textDisplay,
                        radius = g.cellSize * 0.16f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    val layout = pinLabels[index]
                    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y + g.cellSize * 0.2f))
                }
            }
        }
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
                is CellType.Floor -> if (tintFloors && type.roomId in roomIds) {
                    floorFills.getOrPut(type.roomId) { Path() }.addRect(rect)
                } // unassigned floor stays bare

                is CellType.Empty -> {
                    if (wallRunStart < 0) wallRunStart = x
                    if (detailed) when (type.material) {
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

                        Material.Metal -> metal.addRect(rect)
                    } else if (type.material == Material.Metal) metal.addRect(rect)
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
        drawPoints(layer.drywallDots, PointMode.Points, pattern.copy(alpha = pattern.alpha * 0.6f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
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

private class PendingTap(val cell: Vec2, val position: Offset, val job: Job)

private data class RoomBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val tileCount: Int) {
    fun overlaps(other: RoomBounds): Boolean =
        minX <= other.maxX && other.minX <= maxX && minY <= other.maxY && other.minY <= maxY
}

/**
 * Irregular rooms can have overlapping bounding boxes, which would stack their labels. Keep the
 * room with more tiles and drop any label whose box overlaps one already kept. Zoom-independent, so
 * it's computed once per plan rather than per draw.
 */
private fun resolveLabelOverlaps(bounds: Map<Int, RoomBounds>): Map<Int, RoomBounds> {
    val kept = LinkedHashMap<Int, RoomBounds>()
    // roomId as tiebreaker keeps the winner stable when two rooms have equal tile counts.
    bounds.entries.sortedWith(compareByDescending<Map.Entry<Int, RoomBounds>> { it.value.tileCount }.thenBy { it.key })
        .forEach { (roomId, box) -> if (kept.values.none { it.overlaps(box) }) kept[roomId] = box }
    return kept
}

private fun computeRoomBounds(plan: GridPlan): Map<Int, RoomBounds> {
    val bounds = HashMap<Int, RoomBounds>()
    for (y in 0 until plan.height) {
        for (x in 0 until plan.width) {
            val cell = plan.cellAt(x, y)
            if (cell is CellType.Floor) {
                val existing = bounds[cell.roomId]
                bounds[cell.roomId] = if (existing == null) {
                    RoomBounds(x, y, x, y, tileCount = 1)
                } else {
                    RoomBounds(
                        minOf(existing.minX, x),
                        minOf(existing.minY, y),
                        maxOf(existing.maxX, x),
                        maxOf(existing.maxY, y),
                        existing.tileCount + 1,
                    )
                }
            }
        }
    }
    return bounds
}

private fun DrawScope.drawDoorTile(origin: Offset, cellSize: Float, color: Color, strokeWidthPx: Float) {
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(origin.x - cellSize, origin.y),
        size = Size(cellSize * 2, cellSize * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx),
    )
}
