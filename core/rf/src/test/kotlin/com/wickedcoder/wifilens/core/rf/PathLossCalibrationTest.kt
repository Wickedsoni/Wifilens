package com.wickedcoder.wifilens.core.rf

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Vec2
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PathLossCalibrationTest {
    private val open = GridPlan(30, 30, List(900) { CellType.Floor(1) })
    private val router = Vec2(0, 0)
    private val spots = listOf(Vec2(1, 0), Vec2(3, 0), Vec2(6, 2), Vec2(10, 5), Vec2(15, 10), Vec2(20, 20), Vec2(25, 5))

    private fun readings(plan: GridPlan, a: Float, n: Float, noise: (Int) -> Float = { 0f }) =
        spots.mapIndexed { i, pos -> CalibrationSample(pos, predictRssi(plan, router, pos, a, n) + noise(i)) }

    @ParameterizedTest(name = "recovers A={0}, n={1} exactly from noiseless readings")
    @CsvSource("-40, 2.0", "-35, 3.0", "-45, 3.7", "-30, 5.5")
    fun `recovers the model that produced noiseless readings`(a: Float, n: Float) {
        val fit = assertIs<CalibrationResult.Fitted>(fitPathLoss(open, router, readings(open, a, n))).fit

        assertEquals(a, fit.referenceRssiAt1m, 0.01f)
        assertEquals(n, fit.pathLossExponent, 0.01f)
        assertEquals(0f, fit.rmseDb, 0.01f)
        assertEquals(spots.size, fit.sampleCount)
    }

    @Test
    fun `wall losses along each ray are accounted for, so walls don't bias the exponent`() {
        val walled = GridPlan(30, 30, List(900) { i -> if (i % 30 == 8) CellType.Empty(Material.Concrete) else CellType.Floor(1) })

        val fit = assertIs<CalibrationResult.Fitted>(fitPathLoss(walled, router, readings(walled, -40f, 3f))).fit

        assertEquals(3f, fit.pathLossExponent, 0.01f)
        assertEquals(-40f, fit.referenceRssiAt1m, 0.01f)
    }

    @Test
    fun `noisy readings still land close and report their error`() {
        val noise = floatArrayOf(2f, -3f, 1.5f, -2f, 3f, -1f, 2.5f)

        val fit = assertIs<CalibrationResult.Fitted>(fitPathLoss(open, router, readings(open, -40f, 3f) { noise[it] })).fit

        assertEquals(3f, fit.pathLossExponent, 0.35f)
        assertTrue(fit.rmseDb in 1f..3f, "rmse ${fit.rmseDb}")
    }

    @Test
    fun `fewer than five readings are refused`() {
        val result = fitPathLoss(open, router, readings(open, -40f, 3f).take(MIN_CALIBRATION_SAMPLES - 1))

        assertEquals(CalibrationResult.Rejected(CalibrationProblem.TooFewSamples), result)
    }

    @Test
    fun `readings all at about the same distance are refused`() {
        val sameDistance = listOf(Vec2(10, 0), Vec2(0, 10), Vec2(8, 6), Vec2(6, 8), Vec2(10, 1)).map { CalibrationSample(it, -70f) }

        assertEquals(CalibrationResult.Rejected(CalibrationProblem.TooLittleSpread), fitPathLoss(open, router, sameDistance))
    }

    @Test
    fun `an implausible slope is clamped to the physical range`() {
        // Signal *rising* with distance (e.g. the router pin is misplaced) would give a negative n.
        val rising = spots.mapIndexed { i, pos -> CalibrationSample(pos, -80f + i * 5f) }

        val fit = assertIs<CalibrationResult.Fitted>(fitPathLoss(open, router, rising)).fit

        assertEquals(1.5f, fit.pathLossExponent, 0.001f)
    }
}
