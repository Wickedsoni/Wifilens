package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.PlanCalibration

/**
 * The settings predictions should use for this plan: a walk-survey calibration, when the plan has one, replaces the
 * generic path-loss values from Settings (it was fitted to this home's walls and router).
 */
fun AppSettings.calibratedBy(calibration: PlanCalibration?): AppSettings =
    if (calibration == null) {
        this
    } else {
        copy(referenceRssiAt1m = calibration.referenceRssiAt1m, pathLossExponent = calibration.pathLossExponent)
    }
