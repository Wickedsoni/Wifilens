package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.Measurement
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.Vec2
import kotlinx.coroutines.flow.Flow

/** Walk-survey readings and the path-loss calibration fitted from them, per plan (Sprint 7). */
interface SurveyRepository {
    /** One averaged reading per surveyed tile of plan [planId]. */
    fun observeMeasurements(planId: Long): Flow<List<Measurement>>

    /**
     * Folds a reading of [sampleCount] averaged samples into the tile's running average. [bssid] is the access point
     * measured (null when Android hides it); readings of different access points on one tile are kept apart.
     */
    suspend fun addReading(planId: Long, pos: Vec2, bssid: String?, rssi: Float, sampleCount: Int)

    /** Deletes every reading of plan [planId] and its calibration. */
    suspend fun clearMeasurements(planId: Long)

    /** Stores the fitted model for plan [planId]; null removes it (predictions fall back to the Settings values). */
    suspend fun setCalibration(planId: Long, calibration: PlanCalibration?)
}
