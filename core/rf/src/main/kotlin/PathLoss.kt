package com.wickedcoder.wifilens.core.rf

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Log-distance path loss model with wall attenuation:
 * RSSI = A - 10·n·log10(d) - Σ wallLoss(walls crossed)
 *
 * @param referenceRssiAt1m A — signal strength at 1 meter (dBm)
 * @param pathLossExponent  n — decay steepness, 2.0 (open) to 4.0 (dense walls)
 * @param d                 Euclidean distance in tiles, treated as metres, clamped to >= 1
 *                          to avoid log(0)/log of a fraction amplifying the signal.
 * Wall loss only accumulates over [CellType.Empty] cells crossed by the Bresenham line —
 * [CellType.Floor] and [CellType.Door] cells contribute nothing.
 */
fun predictRssi(
    plan: GridPlan,
    routerPos: Vec2,
    targetPos: Vec2,
    referenceRssiAt1m: Float = -40f,
    pathLossExponent: Float = 3.0f,
): Float {
    val dx = (targetPos.x - routerPos.x).toFloat()
    val dy = (targetPos.y - routerPos.y).toFloat()
    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

    val wallLossDb = bresenhamLine(routerPos, targetPos)
        .sumOf { pos ->
            when (val cell = plan.cellAt(pos.x, pos.y)) {
                is CellType.Empty -> cell.material.lossDb.toDouble()
                CellType.Door, is CellType.Floor -> 0.0
            }
        }
        .toFloat()

    return referenceRssiAt1m - 10f * pathLossExponent * log10(distance) - wallLossDb
}
