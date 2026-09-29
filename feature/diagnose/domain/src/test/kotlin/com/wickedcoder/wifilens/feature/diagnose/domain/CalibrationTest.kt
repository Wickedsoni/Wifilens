package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.PlanCalibration
import kotlin.test.Test
import kotlin.test.assertEquals

class CalibrationTest {
    private val user = AppSettings(pathLossExponent = 3f, referenceRssiAt1m = -40f, hapticsEnabled = false)

    @Test
    fun `without a calibration the Settings values are used`() {
        assertEquals(user, user.calibratedBy(null))
    }

    @Test
    fun `a calibration replaces only the path-loss model`() {
        val calibrated = user.calibratedBy(PlanCalibration(referenceRssiAt1m = -36f, pathLossExponent = 3.8f, rmseDb = 2f))

        assertEquals(user.copy(referenceRssiAt1m = -36f, pathLossExponent = 3.8f), calibrated)
    }
}
