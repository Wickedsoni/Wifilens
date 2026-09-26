package com.wickedcoder.wifilens.feature.map.presentation

import kotlin.math.cos
import kotlin.math.sin

data class IsoOffset(val x: Float, val y: Float)

/** Grid-to-screen projection and draw ordering for the isometric map view — the pure-math half of
 * [IsoCanvas], kept separate so the projection logic can be reasoned about without Compose in the
 * picture. */
object IsoProjection {
    /**
     * Grid -> screen in a 2:1 isometric projection, viewed after rotating the grid by `angleRad`
     * about its own centre (the "orbit"). At angle 0 this is exactly the classic un-rotated iso view.
     *
     * The rotation has to happen in grid space *before* the iso squash: rotating and then mapping
     * straight to (x = rx, y = ry) would give a top-down orthographic view, not an isometric one.
     * Coordinates are grid *corner* coordinates — cell (c, r) spans (c..c+1, r..r+1).
     */
    fun toScreen(
        col: Float,
        row: Float,
        gridWidth: Int,
        gridHeight: Int,
        tileW: Float,
        tileH: Float,
        angleRad: Float,
    ): IsoOffset {
        val dx = col - gridWidth / 2f
        val dy = row - gridHeight / 2f
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val rx = dx * cosA - dy * sinA
        val ry = dx * sinA + dy * cosA
        return IsoOffset(
            x = (rx - ry) * tileW / 2f,
            y = (rx + ry) * tileH / 2f,
        )
    }

    /**
     * Painter's algorithm: cells further from the camera first. In the rotated frame the camera
     * looks along +(rx + ry), so depth is the rotated centre's `rx + ry`; ties by `rx` keep the
     * order stable. At angle 0 this reduces to the original (col + row) ordering.
     *
     * Getting this order wrong doesn't crash anything — it's silent. A nearer wall gets painted
     * before a farther one behind it and the farther one draws on top, so floors and walls appear
     * to poke through each other as the view rotates, worst right around the diagonal angles where
     * draw order actually changes cell-to-cell.
     */
    fun isoDrawOrder(width: Int, height: Int, angleRad: Float): List<Pair<Int, Int>> {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        fun rotatedX(col: Int, row: Int) = (col + 0.5f - width / 2f) * cosA - (row + 0.5f - height / 2f) * sinA

        fun rotatedY(col: Int, row: Int) = (col + 0.5f - width / 2f) * sinA + (row + 0.5f - height / 2f) * cosA

        return (0 until width)
            .flatMap { col -> (0 until height).map { row -> col to row } }
            .sortedWith(
                compareBy(
                    { rotatedX(it.first, it.second) + rotatedY(it.first, it.second) },
                    { rotatedX(it.first, it.second) },
                ),
            )
    }
}
