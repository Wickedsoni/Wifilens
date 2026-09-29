package com.wickedcoder.wifilens.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Signal history (scan samples) and hourly channel congestion (Sprint 8). */
@Dao
interface HistoryDao {
    @Insert
    suspend fun insertSamples(samples: List<ScanSampleEntity>)

    @Query("SELECT * FROM scan_sample WHERE bssid = :bssid AND timestamp >= :since ORDER BY timestamp")
    fun observeSamples(bssid: String, since: Long): Flow<List<ScanSampleEntity>>

    /** Latest sample time per access point since [since], for the recording throttle after a restart. */
    @Query("SELECT bssid, MAX(timestamp) AS timestamp FROM scan_sample WHERE timestamp >= :since GROUP BY bssid")
    suspend fun lastSampleTimes(since: Long): List<LastSample>

    @Query("DELETE FROM scan_sample WHERE timestamp < :before")
    suspend fun deleteSamplesBefore(before: Long)

    @Query("SELECT * FROM channel_congestion WHERE hourStart = :hourStart AND band = :band AND channel = :channel")
    suspend fun findCongestion(hourStart: Long, band: String, channel: Int): ChannelCongestionEntity?

    @Upsert
    suspend fun upsertCongestion(rows: List<ChannelCongestionEntity>)

    @Query("SELECT * FROM channel_congestion WHERE band = :band AND hourStart >= :since ORDER BY hourStart, channel")
    fun observeCongestion(band: String, since: Long): Flow<List<ChannelCongestionEntity>>

    @Query("DELETE FROM channel_congestion WHERE hourStart < :before")
    suspend fun deleteCongestionBefore(before: Long)

    /** Keeps the busiest reading of each channel within an hour: the roll-up answers "how crowded did it get". */
    @Transaction
    suspend fun mergeCongestion(rows: List<ChannelCongestionEntity>) {
        upsertCongestion(
            rows.map { row ->
                val existing = findCongestion(row.hourStart, row.band, row.channel) ?: return@map row
                row.copy(
                    networkCount = maxOf(existing.networkCount, row.networkCount),
                    strongestRssi = maxOf(existing.strongestRssi, row.strongestRssi),
                    congestionScore = maxOf(existing.congestionScore, row.congestionScore),
                )
            },
        )
    }
}

/** Completed speed tests (Sprint 8). */
@Dao
interface SpeedTestDao {
    @Insert
    suspend fun insertSpeedTest(test: SpeedTestEntity)

    @Query("SELECT * FROM speed_test ORDER BY timestamp DESC")
    fun observeSpeedTests(): Flow<List<SpeedTestEntity>>

    @Query("DELETE FROM speed_test WHERE id NOT IN (SELECT id FROM speed_test ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimSpeedTests(keep: Int)
}

/** Row of [HistoryDao.lastSampleTimes]. */
data class LastSample(val bssid: String, val timestamp: Long)
