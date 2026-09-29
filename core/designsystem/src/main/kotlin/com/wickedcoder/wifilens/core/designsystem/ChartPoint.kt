package com.wickedcoder.wifilens.core.designsystem

/** One chart sample: [x] in the caller's unit (e.g. epoch millis), [y] the value (e.g. dBm). */
data class ChartPoint(val x: Float, val y: Float)

/**
 * Reduces [points] (sorted by x) to at most about `2 × buckets` points by keeping each x-bucket's lowest and highest
 * sample in time order. Unlike averaging, dips and spikes survive, which is what a signal trace is read for.
 */
fun downsampleMinMax(points: List<ChartPoint>, buckets: Int): List<ChartPoint> {
    if (points.size <= buckets * 2 || buckets <= 0) return points
    val first = points.first().x
    val span = (points.last().x - first).takeIf { it > 0f } ?: return points
    return points
        .groupBy { ((it.x - first) / span * (buckets - 1)).toInt() }
        .values
        .flatMap { bucket ->
            val low = bucket.minBy { it.y }
            val high = bucket.maxBy { it.y }
            when {
                low === high -> listOf(low)
                low.x <= high.x -> listOf(low, high)
                else -> listOf(high, low)
            }
        }
}
