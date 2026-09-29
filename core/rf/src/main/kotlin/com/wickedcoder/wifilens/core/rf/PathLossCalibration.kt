package com.wickedcoder.wifilens.core.rf

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Vec2
import kotlin.math.log10
import kotlin.math.sqrt

/** One walk-survey reading: averaged measured RSSI (dBm) on a tile. */
data class CalibrationSample(val pos: Vec2, val rssi: Float)

/** A fitted path-loss model for one plan: the A and n of [predictRssi], and how well it matches the readings. */
data class PathLossFit(
    val referenceRssiAt1m: Float,
    val pathLossExponent: Float,
    /** Root-mean-square error of the fitted model against the readings, in dB. */
    val rmseDb: Float,
    val sampleCount: Int,
)

/** Why a fit wasn't produced. */
enum class CalibrationProblem { TooFewSamples, TooLittleSpread }

sealed interface CalibrationResult {
    data class Fitted(val fit: PathLossFit) : CalibrationResult

    data class Rejected(val problem: CalibrationProblem) : CalibrationResult
}

const val MIN_CALIBRATION_SAMPLES = 5

/** Readings must span at least this range of 10·log10(distance), i.e. about a 2x distance ratio (3 dB). */
private const val MIN_LOG_DISTANCE_SPREAD = 3.0

/** Physically plausible exponents: free space is 2; dense indoor rarely exceeds 6. Values outside get clamped. */
private const val MIN_EXPONENT = 1.5
private const val MAX_EXPONENT = 6.0

/**
 * Least-squares calibration of the log-distance model used by [predictRssi]:
 * `rssi = A − 10·n·log10(d) − wallLoss`. Wall loss along each ray is known from the plan, so moving it to the
 * left turns this into a straight line, `rssi + wallLoss = A − n·x` with `x = 10·log10(max(d, 1))`, and ordinary
 * least squares gives A and n. The fit is refused with too few readings or when they sit at nearly the same
 * distance (n would be meaningless); n is clamped to a plausible range and A refitted for the clamped n.
 */
fun fitPathLoss(plan: GridPlan, routerPos: Vec2, samples: List<CalibrationSample>): CalibrationResult {
    if (samples.size < MIN_CALIBRATION_SAMPLES) return CalibrationResult.Rejected(CalibrationProblem.TooFewSamples)

    val xs = samples.map { 10.0 * log10(distance(routerPos, it.pos)) }
    val ys = samples.map { it.rssi + wallLossDb(plan, routerPos, it.pos) }
    if (xs.max() - xs.min() < MIN_LOG_DISTANCE_SPREAD) return CalibrationResult.Rejected(CalibrationProblem.TooLittleSpread)

    val meanX = xs.average()
    val meanY = ys.average()
    val sxx = xs.sumOf { (it - meanX) * (it - meanX) }
    val sxy = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
    val exponent = (-sxy / sxx).coerceIn(MIN_EXPONENT, MAX_EXPONENT) // slope is −n
    val reference = meanY + exponent * meanX // best A for the (possibly clamped) n

    val rmse = sqrt(xs.indices.sumOf { i -> (reference - exponent * xs[i] - ys[i]).let { it * it } } / samples.size)
    return CalibrationResult.Fitted(
        PathLossFit(
            referenceRssiAt1m = reference.toFloat(),
            pathLossExponent = exponent.toFloat(),
            rmseDb = rmse.toFloat(),
            sampleCount = samples.size,
        ),
    )
}

private fun distance(a: Vec2, b: Vec2): Double {
    val dx = (b.x - a.x).toDouble()
    val dy = (b.y - a.y).toDouble()
    return sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
}

/** Same wall accounting as [predictRssi]: loss of every wall tile on the Bresenham ray. */
private fun wallLossDb(plan: GridPlan, from: Vec2, to: Vec2): Double =
    bresenhamLine(from, to).sumOf { pos ->
        when (val cell = plan.cellAt(pos.x, pos.y)) {
            is CellType.Empty -> cell.material.lossDb.toDouble()
            CellType.Door, is CellType.Floor -> 0.0
        }
    }
