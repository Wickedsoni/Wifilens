package com.wickedcoder.wifilens.core.model

/** An integer grid coordinate — a cell index, not a pixel. Used for everything from a painted
 * tile's position to router/device pin placement, so the same value works whether it's being
 * stored, walked by [bresenhamLine], or converted to screen space by the map renderers. */
data class Vec2(val x: Int, val y: Int) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
}
