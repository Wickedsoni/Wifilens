package com.wickedcoder.wifilens.core.history

import com.wickedcoder.wifilens.core.database.ChannelCongestionEntity
import com.wickedcoder.wifilens.core.database.HistoryDao
import com.wickedcoder.wifilens.core.database.ScanSampleEntity
import com.wickedcoder.wifilens.core.database.SpeedTestEntity
import com.wickedcoder.wifilens.core.model.CONGESTION_RETENTION_DAYS
import com.wickedcoder.wifilens.core.model.ChannelCongestion
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.SAMPLE_RETENTION_DAYS
import com.wickedcoder.wifilens.core.model.SPEED_TEST_KEEP_COUNT
import com.wickedcoder.wifilens.core.model.SignalPoint
import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.toWifiBand
import com.wickedcoder.wifilens.core.model.toWifiChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** At most one stored sample per access point per this interval: enough for a trend, small enough to keep a week. */
internal val SAMPLE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30)

private val HOUR_MS = TimeUnit.HOURS.toMillis(1)

/** [HistoryRepository] on the Room history tables. Singleton: the recording throttle lives in memory. */
@Singleton
class HistoryRepositoryImpl
    @Inject
    constructor(
        private val dao: HistoryDao,
    ) : HistoryRepository {
        private val throttleLock = Mutex()

        /** Last stored sample time per BSSID; seeded from the database once, so a restart doesn't double-record. */
        private var lastRecorded: MutableMap<String, Long>? = null

        override fun observeSignal(bssid: String, sinceMillis: Long): Flow<List<SignalPoint>> =
            dao.observeSamples(bssid, sinceMillis).map { rows -> rows.map { SignalPoint(it.timestamp, it.rssi) } }

        override fun observeCongestion(band: String, sinceMillis: Long): Flow<List<ChannelCongestion>> =
            dao.observeCongestion(band, sinceMillis).map { rows ->
                rows.map { ChannelCongestion(it.hourStart, it.band, it.channel, it.networkCount, it.strongestRssi, it.congestionScore) }
            }

        override fun observeSpeedTests(): Flow<List<SpeedTestRecord>> =
            dao.observeSpeedTests().map { rows ->
                rows.map { SpeedTestRecord(it.timestamp, it.downloadMbps.toFloat(), it.linkSpeedMbps, it.rssi, it.frequencyMhz) }
            }

        override suspend fun recordSpeedTest(record: SpeedTestRecord) {
            dao.insertSpeedTest(
                SpeedTestEntity(
                    timestamp = record.timestampMillis,
                    downloadMbps = record.downloadMbps.toDouble(),
                    linkSpeedMbps = record.linkSpeedMbps,
                    rssi = record.rssi,
                    frequencyMhz = record.frequencyMhz,
                ),
            )
        }

        override suspend fun recordScan(results: List<WifiScanResult>, timestampMillis: Long) {
            val due = throttleLock.withLock {
                val last = lastRecorded ?: dao
                    .lastSampleTimes(timestampMillis - SAMPLE_INTERVAL_MS)
                    .associate { it.bssid to it.timestamp }
                    .toMutableMap()
                    .also { lastRecorded = it }
                results
                    .filter { it.bssid.isNotEmpty() && timestampMillis - (last[it.bssid] ?: Long.MIN_VALUE / 2) >= SAMPLE_INTERVAL_MS }
                    .onEach { last[it.bssid] = timestampMillis }
            }
            if (due.isNotEmpty()) dao.insertSamples(due.map { it.toSample(timestampMillis) })
            val rollUp = congestionRollUp(results, hourStart = timestampMillis - timestampMillis % HOUR_MS)
            if (rollUp.isNotEmpty()) dao.mergeCongestion(rollUp)
        }

        override suspend fun prune(nowMillis: Long) {
            dao.deleteSamplesBefore(nowMillis - TimeUnit.DAYS.toMillis(SAMPLE_RETENTION_DAYS.toLong()))
            dao.deleteCongestionBefore(nowMillis - TimeUnit.DAYS.toMillis(CONGESTION_RETENTION_DAYS.toLong()))
            dao.trimSpeedTests(SPEED_TEST_KEEP_COUNT)
        }
    }

private fun WifiScanResult.toSample(timestamp: Long) = ScanSampleEntity(
    bssid = bssid,
    ssid = ssid,
    rssi = rssi,
    channel = frequencyMhz.toWifiChannel(),
    band = frequencyMhz.toWifiBand(),
    timestamp = timestamp,
)

/** Signal above this floor adds to a channel's congestion; a network at −95 dBm or weaker barely interferes. */
private const val CONGESTION_FLOOR_DBM = -95

/**
 * One scan's congestion per (band, channel): how many networks sit on the channel, the strongest, and a score that
 * weighs each network by how loud it is (`rssi − (−95)`, so a neighbour at −45 counts ten times one at −90).
 */
internal fun congestionRollUp(results: List<WifiScanResult>, hourStart: Long): List<ChannelCongestionEntity> =
    results
        .groupBy { it.frequencyMhz.toWifiBand() to it.frequencyMhz.toWifiChannel() }
        .filterKeys { (band, channel) -> band != "?" && channel > 0 }
        .map { (key, networks) ->
            ChannelCongestionEntity(
                hourStart = hourStart,
                band = key.first,
                channel = key.second,
                networkCount = networks.size,
                strongestRssi = networks.maxOf { it.rssi },
                congestionScore = networks.sumOf { (it.rssi - CONGESTION_FLOOR_DBM).coerceAtLeast(0) },
            )
        }
