package com.wickedcoder.wifilens.core.model

import kotlinx.coroutines.flow.Flow

/** One recorded signal reading of an access point. */
data class SignalPoint(val timestampMillis: Long, val rssi: Int)

/** Congestion of one channel during one clock hour (the hourly roll-up kept after raw samples are pruned). */
data class ChannelCongestion(
    val hourStartMillis: Long,
    val band: String,
    val channel: Int,
    val networkCount: Int,
    val strongestRssi: Int,
    val congestionScore: Int,
)

/** One completed speed test and the connection it ran on. */
data class SpeedTestRecord(
    val timestampMillis: Long,
    val downloadMbps: Float,
    val linkSpeedMbps: Int? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
)

/**
 * Signal, congestion and speed-test history, recorded on the device only (never uploaded). Scan samples are
 * recorded while the app is in the foreground and pruned after [SAMPLE_RETENTION_DAYS] days.
 */
interface HistoryRepository {
    /** Readings of access point [bssid] since [sinceMillis], oldest first. */
    fun observeSignal(bssid: String, sinceMillis: Long): Flow<List<SignalPoint>>

    /** Hourly congestion roll-ups of [band] since [sinceMillis], oldest first. */
    fun observeCongestion(band: String, sinceMillis: Long): Flow<List<ChannelCongestion>>

    /** Every stored speed test, newest first. */
    fun observeSpeedTests(): Flow<List<SpeedTestRecord>>

    suspend fun recordSpeedTest(record: SpeedTestRecord)

    /** Records one fresh scan: throttled samples per access point plus the hour's congestion roll-up. */
    suspend fun recordScan(results: List<WifiScanResult>, timestampMillis: Long)

    /** Deletes history older than the retention windows, relative to [nowMillis]. */
    suspend fun prune(nowMillis: Long)
}

/** Raw per-scan samples are kept a week; hourly roll-ups a month; the newest speed tests are kept regardless. */
const val SAMPLE_RETENTION_DAYS = 7
const val CONGESTION_RETENTION_DAYS = 30
const val SPEED_TEST_KEEP_COUNT = 500
