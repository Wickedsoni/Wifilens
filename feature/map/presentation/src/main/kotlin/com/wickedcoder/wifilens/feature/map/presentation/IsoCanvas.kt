package com.wickedcoder.wifilens.feature.map.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wickedcoder.wifilens.core.designsystem.isDark
import com.wickedcoder.wifilens.core.designsystem.roomColor
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Read-only isometric render of the same [GridPlan] the 2D editor paints — no 3D engine, just a
 * Canvas projection. [wallRiseProgress] (0f flat -> 1f full height) drives the "walls rise"
 * entrance animation; [rotationAngle] (radians) orbits the view around the plan's centre.
 *
 * Tile size is fitted so the plan stays fully on screen at *every* orbit angle (its footprint is
 * widest along a diagonal), and stays constant while orbiting rather than pulsing. [zoomScale]
 * multiplies that fitted size (tiles and wall height together), so a zoom >1 can push the plan
 * past the canvas edge by design.
 *
 * A pinch in progress doesn't redraw: [pinchScale] (read only in the layer, so it neither recomposes nor redraws)
 * scales the drawn scene as a layer about the plan's own origin, matching exactly what a redraw at
 * `zoomScale * pinchScale` would put on screen for tiles and walls. The caller folds it into [zoomScale] when the
 * fingers lift, for one crisp redraw. Zooming in, the scene is cached offscreen so each frame only scales a texture;
 * zooming out it isn't (a texture would clip at the old edges), and while [pinching] nothing is culled, so the
 * edges that come into view are already drawn.
 */
@Composable
fun IsoCanvas(
    plan: GridPlan,
    rooms: List<Room>,
    routerPos: Vec2?,
    devicePins: List<DevicePin>,
    wallRiseProgress: Float,
    rotationAngle: Float,
    modifier: Modifier = Modifier,
    /** Pinch zoom multiplier on top of the fit-to-screen tile size; 1f = fitted. */
    zoomScale: Float = 1f,
    pinchScale: () -> Float = { 1f },
    pinching: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val roomIds = remember(rooms) { rooms.map { it.id }.toSet() }
    val drawOrder = remember(plan.width, plan.height, rotationAngle) {
        IsoProjection.isoDrawOrder(plan.width, plan.height, rotationAngle)
    }

    val isoDescription = stringResource(R.string.map_iso_description)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = isoDescription }
            .graphicsLayer {
                val scale = pinchScale()
                compositingStrategy = if (scale > 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                if (scale == 1f) {
                    scaleX = 1f
                    scaleY = 1f
                    translationY = 0f
                } else {
                    val drawn = isoFrame(size, plan, zoomScale)
                    val target = isoFrame(size, plan, zoomScale * scale)
                    transformOrigin = TransformOrigin(drawn.originX / size.width, drawn.originY / size.height)
                    scaleX = scale
                    scaleY = scale
                    translationY = target.originY - drawn.originY
                }
            },
    ) {
        val pinHeightPx = PIN_HEIGHT.toPx()
        val frame = isoFrame(size, plan, zoomScale)
        val tileW = frame.tileW
        val tileH = tileW * 0.5f
        val fullWallH = frame.fullWallH
        val wallH = fullWallH * wallRiseProgress
        val originX = frame.originX
        val originY = frame.originY

        fun screenOf(col: Float, row: Float): Offset {
            val p = IsoProjection.toScreen(col, row, plan.width, plan.height, tileW, tileH, rotationAngle)
            return Offset(originX + p.x, originY + p.y)
        }

        // One scratch Path reused for every polygon: allocating ~3 Paths per cell per frame is what made
        // large plans stall. drawPath consumes it immediately, so reuse is safe.
        val scratch = Path()

        fun polygon(a: Offset, b: Offset, c: Offset, d: Offset): Path = scratch.apply {
            rewind()
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            lineTo(d.x, d.y)
            close()
        }
        // Big plans: tiles are only a few px wide, so side faces and outlines are invisible but cost
        // 4x the draw calls. Flat tops only above the threshold.
        val simple = plan.width * plan.height > SIMPLE_RENDER_TILE_THRESHOLD
        val cull = tileW * 2f + fullWallH

        val cosA = cos(rotationAngle)
        val sinA = sin(rotationAngle)
        // Outward grid-space normals of the four edges, in corner order c0->c1->c2->c3.
        val edgeNormals = arrayOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)
        val lift = Offset(0f, -wallH)

        drawOrder.forEach { (col, row) ->
            val c0 = screenOf(col.toFloat(), row.toFloat())
            val c1 = screenOf(col + 1f, row.toFloat())
            val c2 = screenOf(col + 1f, row + 1f)
            val c3 = screenOf(col.toFloat(), row + 1f)
            val cx = (c0.x + c2.x) / 2f
            val cy = (c0.y + c2.y) / 2f
            // Zoomed in, most cells are off screen; skip them before doing any drawing work.
            val offScreen = cx < -cull || cx > size.width + cull || cy < -cull - fullWallH || cy > size.height + cull
            if (offScreen && !pinching) return@forEach
            val corners = arrayOf(c0, c1, c2, c3)

            when (val cell = plan.cellAt(col, row)) {
                is CellType.Floor -> {
                    // Same palette as the 2D editor: 60% fill + full-strength outline in the room's colour.
                    val roomColor = if (cell.roomId in roomIds) roomColor(cell.roomId, colors.isDark) else null
                    drawPath(polygon(c0, c1, c2, c3), color = roomColor?.copy(alpha = 0.6f) ?: colors.outlineVariant)
                    if (!simple) {
                        drawPath(
                            polygon(c0, c1, c2, c3),
                            color = roomColor ?: colors.outline,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }

                is CellType.Empty -> {
                    val materialColor = isoMaterialColor(cell.material, colors.outline)
                    if (wallRiseProgress > 0f && !simple) {
                        // Only faces turned toward the camera (outward normal has positive depth in
                        // the rotated frame) — back faces would show through the translucent fill.
                        for (i in 0 until 4) {
                            val (nx0, ny0) = edgeNormals[i]
                            val nx = nx0 * cosA - ny0 * sinA
                            val ny = nx0 * sinA + ny0 * cosA
                            if (nx + ny <= 0.001f) continue
                            val a = corners[i]
                            val b = corners[(i + 1) % 4]
                            val shade = 0.5f + 0.2f * nx.coerceIn(0f, 1f)
                            drawPath(
                                polygon(a, b, b + lift, a + lift),
                                color = materialColor.copy(alpha = materialColor.alpha * shade),
                            )
                        }
                        drawPath(
                            polygon(c0 + lift, c1 + lift, c2 + lift, c3 + lift),
                            color = materialColor.copy(alpha = materialColor.alpha * 0.9f),
                        )
                    } else {
                        // Also the large-plan path: raised walls are drawn as one flat, slightly stronger tile.
                        val alpha = if (simple) 0.9f else 0.6f
                        drawPath(polygon(c0, c1, c2, c3), color = materialColor.copy(alpha = materialColor.alpha * alpha))
                    }
                }

                CellType.Door -> {
                    drawPath(polygon(c0, c1, c2, c3), color = colors.outline.copy(alpha = 0.6f))
                }
            }
        }

        routerPos?.let { pos ->
            val base = screenOf(pos.x + 0.5f, pos.y + 0.5f)
            val topY = base.y - wallH
            drawLine(colors.onSurface, Offset(base.x, topY), Offset(base.x, topY - pinHeightPx), strokeWidth = 1.5.dp.toPx())
            drawCircle(colors.onSurface, radius = 4.dp.toPx(), center = Offset(base.x, topY - pinHeightPx))
        }

        devicePins.forEach { pin ->
            val base = screenOf(pin.pos.x + 0.5f, pin.pos.y + 0.5f)
            val topY = base.y - wallH
            drawLine(colors.onSurface, Offset(base.x, topY), Offset(base.x, topY - pinHeightPx), strokeWidth = 1.5.dp.toPx())
            drawCircle(
                colors.onSurface,
                radius = 4.dp.toPx(),
                center = Offset(base.x, topY - pinHeightPx),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private const val WALL_HEIGHT_RATIO = 0.8f
private val PIN_HEIGHT = 24.dp

/** Tile width, wall height and screen origin of the plan's centre, for a canvas of [size] at [zoom]. */
private class IsoFrame(val tileW: Float, val fullWallH: Float, val originX: Float, val originY: Float)

private fun Density.isoFrame(size: Size, plan: GridPlan, zoom: Float): IsoFrame {
    val pinClearancePx = PIN_HEIGHT.toPx() + 4.dp.toPx()
    // Worst-case (over all angles) half-extent of the footprint is R * sqrt(2) tile-halves,
    // where R is the circumradius of the plan in cells.
    val reach = hypot(plan.width.toFloat(), plan.height.toFloat()) / 2f * sqrt(2f)
    val fitWidth = size.width * 0.92f / reach
    val fitHeight = (size.height * 0.92f - pinClearancePx) / (reach / 2f + WALL_HEIGHT_RATIO)
    val baseTileW = min(48.dp.toPx(), min(fitWidth, fitHeight)).coerceAtLeast(4.dp.toPx())
    val tileW = baseTileW * zoom
    val fullWallH = tileW * WALL_HEIGHT_RATIO
    // IsoProjection.toScreen is already centred on the plan's middle, so the canvas centre is the right origin at
    // any zoom; only what sticks up (walls scale with tileW, pins don't) shifts Y. Shift down by half of that so the
    // plan sits visually centred.
    return IsoFrame(tileW, fullWallH, size.width / 2f, size.height / 2f + (fullWallH + pinClearancePx) / 2f)
}

private const val SIMPLE_RENDER_TILE_THRESHOLD = 2_500

/** Wood/Glass/Brick/Concrete/Metal are fixed material colours by design; only Drywall follows the theme. */
private fun isoMaterialColor(material: Material, drywallColor: Color): Color = when (material) {
    Material.Drywall -> drywallColor
    Material.Wood -> Color(0xFF8B7355)
    Material.Glass -> Color(0xFF88BBDD)
    Material.Brick -> Color(0xFF8B4513)
    Material.Concrete -> Color(0xFF888888)
    Material.Metal -> Color(0xFF666666)
}
