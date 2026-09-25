package com.wickedcoder.wifilens.core.rf

import com.wickedcoder.wifilens.core.model.Vec2
import kotlin.math.abs

/**
 * Bresenham's line algorithm — integer-only grid traversal.
 * Returns every cell the straight line from [from] to [to] passes through, inclusive of both ends.
 */
fun bresenhamLine(from: Vec2, to: Vec2): List<Vec2> {
    val cells = mutableListOf<Vec2>()

    val dx = abs(to.x - from.x)
    val dy = abs(to.y - from.y)
    val sx = if (from.x < to.x) 1 else -1
    val sy = if (from.y < to.y) 1 else -1
    var error = dx - dy

    var x = from.x
    var y = from.y

    while (true) {
        cells.add(Vec2(x, y))
        if (x == to.x && y == to.y) break

        val e2 = 2 * error
        if (e2 > -dy) {
            error -= dy
            x += sx
        }
        if (e2 < dx) {
            error += dx
            y += sy
        }
    }

    return cells
}
