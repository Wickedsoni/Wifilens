package com.wickedcoder.wifilens.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Walk-survey measurements, one row per plan/tile/access point, averaged as readings arrive. */
@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement WHERE planId = :planId")
    fun observeForPlan(planId: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE planId = :planId")
    suspend fun forPlan(planId: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurement WHERE planId = :planId AND x = :x AND y = :y AND bssid = :bssid")
    suspend fun find(planId: Long, x: Int, y: Int, bssid: String): MeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(measurement: MeasurementEntity)

    @Query("DELETE FROM measurement WHERE planId = :planId")
    suspend fun deleteForPlan(planId: Long)

    /** Folds one reading into the tile's running average (a new tile starts at that reading). */
    @Transaction
    suspend fun addReading(planId: Long, x: Int, y: Int, bssid: String, rssi: Double, samples: Int, now: Long) {
        val existing = find(planId, x, y, bssid)
        val count = (existing?.sampleCount ?: 0) + samples
        val average = if (existing == null) rssi else (existing.rssiAvg * existing.sampleCount + rssi * samples) / count
        upsert(
            MeasurementEntity(
                id = existing?.id ?: 0,
                planId = planId,
                x = x,
                y = y,
                bssid = bssid,
                rssiAvg = average,
                sampleCount = count,
                updatedAt = now,
            ),
        )
    }
}
