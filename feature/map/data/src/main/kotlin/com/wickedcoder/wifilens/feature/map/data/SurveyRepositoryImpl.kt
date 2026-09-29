package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.database.MeasurementDao
import com.wickedcoder.wifilens.core.database.MeasurementEntity
import com.wickedcoder.wifilens.core.database.PlanDao
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.model.Measurement
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.SurveyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Key used when Android redacts the connected BSSID; such readings still count, grouped together. */
private const val UNKNOWN_BSSID = ""

/** [SurveyRepository] backed by the `measurement` table and the plan row's calibration columns (ADR 0007). */
class SurveyRepositoryImpl
    @Inject
    constructor(
        private val measurementDao: MeasurementDao,
        private val planDao: PlanDao,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : SurveyRepository {
        override fun observeMeasurements(planId: Long): Flow<List<Measurement>> =
            measurementDao.observeForPlan(planId).map(::mergePerTile)

        override suspend fun addReading(planId: Long, pos: Vec2, bssid: String?, rssi: Float, sampleCount: Int) =
            measurementDao.addReading(
                planId = planId,
                x = pos.x,
                y = pos.y,
                bssid = bssid ?: UNKNOWN_BSSID,
                rssi = rssi.toDouble(),
                samples = sampleCount,
                now = clock.nowMillis(),
            )

        override suspend fun clearMeasurements(planId: Long) = transactions.run {
            measurementDao.deleteForPlan(planId)
            planDao.setCalibration(planId, referenceRssi = null, exponent = null, rmseDb = null)
        }

        override suspend fun setCalibration(planId: Long, calibration: PlanCalibration?) =
            planDao.setCalibration(
                planId = planId,
                referenceRssi = calibration?.referenceRssiAt1m?.toDouble(),
                exponent = calibration?.pathLossExponent?.toDouble(),
                rmseDb = calibration?.rmseDb?.toDouble(),
            )
    }

/**
 * One value per tile. A tile can hold readings of several access points (mesh nodes, band steering); the one read
 * most often there wins, which is the access point the phone actually used on that spot.
 */
internal fun mergePerTile(rows: List<MeasurementEntity>): List<Measurement> =
    rows
        .groupBy { Vec2(it.x, it.y) }
        .map { (pos, readings) ->
            val best = readings.maxBy { it.sampleCount }
            Measurement(pos = pos, rssi = best.rssiAvg.toFloat(), sampleCount = best.sampleCount)
        }.sortedWith(compareBy({ it.pos.y }, { it.pos.x }))
