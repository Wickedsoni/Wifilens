package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.core.rf.predictRssi

/** Progress is reported every this many tiles (and on the last one), not per tile, to keep updates cheap. */
private const val PROGRESS_STEP_TILES = 25

/**
 * Tries every walkable tile as the router position and keeps the one where the *weakest* device is
 * strongest. Worst case rather than average, so a tile that is great for one device and terrible for
 * another never beats a balanced one. Pure and CPU-heavy: callers run it off the main thread.
 */
class FindBestRouterSpot {
    /**
     * @param onProgress called with 0..1 as tiles are evaluated.
     * @return null when there are no device pins to optimise for.
     */
    operator fun invoke(
        plan: GridPlan,
        devices: List<DevicePin>,
        currentRouter: Vec2?,
        settings: AppSettings,
        onProgress: (Float) -> Unit = {},
    ): BestSpot? {
        if (devices.isEmpty()) return null

        val walkableTiles = buildList {
            for (y in 0 until plan.height) {
                for (x in 0 until plan.width) {
                    if (plan.isWalkable(x, y)) add(Vec2(x, y))
                }
            }
        }
        val total = walkableTiles.size.coerceAtLeast(1)

        var bestTile: Vec2? = null
        var bestWorstCase = Float.NEGATIVE_INFINITY
        var currentWorstCase = Float.NEGATIVE_INFINITY
        val scores = mutableMapOf<Vec2, Float>()

        walkableTiles.forEachIndexed { index, candidate ->
            val worstCase = devices.minOf {
                predictRssi(plan, candidate, it.pos, settings.referenceRssiAt1m, settings.pathLossExponent)
            }
            scores[candidate] = worstCase
            if (worstCase > bestWorstCase) {
                bestWorstCase = worstCase
                bestTile = candidate
            }
            if (candidate == currentRouter) currentWorstCase = worstCase

            if (index % PROGRESS_STEP_TILES == 0 || index == walkableTiles.lastIndex) {
                onProgress((index + 1) / total.toFloat())
            }
        }

        val alreadyOptimal = currentRouter != null && bestTile == currentRouter
        return BestSpot(
            bestTile = bestTile,
            gainDb = when {
                alreadyOptimal -> 0f
                currentRouter != null -> bestWorstCase - currentWorstCase
                else -> null
            },
            alreadyOptimal = alreadyOptimal,
            scores = scores,
        )
    }
}
