package com.wickedcoder.wifilens.core.history

import com.wickedcoder.wifilens.core.database.ChannelCongestionEntity
import com.wickedcoder.wifilens.core.database.HistoryDao
import com.wickedcoder.wifilens.core.database.LastSample
import com.wickedcoder.wifilens.core.database.ScanSampleEntity
import com.wickedcoder.wifilens.core.database.SpeedTestDao
import com.wickedcoder.wifilens.core.database.SpeedTestEntity
import com.wickedcoder.wifilens.core.model.WifiScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRepositoryImplTest {
    private val dao = FakeHistoryDao()
    private val speedTests = FakeSpeedTestDao()
    private val repository = HistoryRepositoryImpl(dao, speedTests)

    private fun ap(bssid: String, rssi: Int, mhz: Int = 2437) = WifiScanResult("Net-$bssid", bssid, rssi, mhz, "[WPA2-PSK-CCMP]")

    @Test
    fun `each access point is sampled at most once per interval`() = runTest {
        repository.recordScan(listOf(ap("a", -50), ap("b", -60)), timestampMillis = 0)
        repository.recordScan(listOf(ap("a", -51), ap("b", -61)), timestampMillis = SAMPLE_INTERVAL_MS - 1)
        repository.recordScan(listOf(ap("a", -52)), timestampMillis = SAMPLE_INTERVAL_MS)

        assertEquals(listOf("a" to 0L, "b" to 0L, "a" to SAMPLE_INTERVAL_MS), dao.samples.map { it.bssid to it.timestamp })
    }

    @Test
    fun `the throttle survives a restart by reading the last stored samples`() = runTest {
        dao.samples += ScanSampleEntity(bssid = "a", ssid = "Net-a", rssi = -50, channel = 6, band = "2.4", timestamp = 1_000)

        HistoryRepositoryImpl(dao, speedTests).recordScan(listOf(ap("a", -55)), timestampMillis = 1_000 + SAMPLE_INTERVAL_MS / 2)

        assertEquals(1, dao.samples.size)
    }

    @Test
    fun `the roll-up counts networks per channel and weighs them by loudness`() {
        val rows = congestionRollUp(listOf(ap("a", -45), ap("b", -85), ap("c", -70, mhz = 5180)), hourStart = 0)
            .associateBy { it.band to it.channel }

        val ch6 = rows.getValue("2.4" to 6)
        assertEquals(2, ch6.networkCount)
        assertEquals(-45, ch6.strongestRssi)
        assertEquals(50 + 10, ch6.congestionScore)
        assertEquals(1, rows.getValue("5" to 36).networkCount)
    }

    @Test
    fun `within one hour the roll-up keeps the busiest reading`() = runTest {
        repository.recordScan(listOf(ap("a", -45), ap("b", -50)), timestampMillis = 0)
        repository.recordScan(listOf(ap("a", -60)), timestampMillis = 60_000)

        val ch6 = dao.congestion.single()
        assertEquals(2, ch6.networkCount)
        assertEquals(-45, ch6.strongestRssi)
    }
}

private class FakeHistoryDao : HistoryDao {
    val samples = mutableListOf<ScanSampleEntity>()
    val congestion = mutableListOf<ChannelCongestionEntity>()

    override suspend fun insertSamples(samples: List<ScanSampleEntity>) {
        this.samples += samples
    }

    override fun observeSamples(bssid: String, since: Long): Flow<List<ScanSampleEntity>> = emptyFlow()

    override suspend fun lastSampleTimes(since: Long): List<LastSample> =
        samples.filter { it.timestamp >= since }.groupBy { it.bssid }.map { (b, s) -> LastSample(b, s.maxOf { it.timestamp }) }

    override suspend fun deleteSamplesBefore(before: Long) {
        samples.removeAll { it.timestamp < before }
    }

    override suspend fun findCongestion(hourStart: Long, band: String, channel: Int): ChannelCongestionEntity? =
        congestion.find { it.hourStart == hourStart && it.band == band && it.channel == channel }

    override suspend fun upsertCongestion(rows: List<ChannelCongestionEntity>) {
        rows.forEach { row ->
            congestion.removeAll { it.hourStart == row.hourStart && it.band == row.band && it.channel == row.channel }
            congestion += row
        }
    }

    override fun observeCongestion(band: String, since: Long): Flow<List<ChannelCongestionEntity>> = emptyFlow()

    override suspend fun deleteCongestionBefore(before: Long) = Unit
}

private class FakeSpeedTestDao : SpeedTestDao {
    override suspend fun insertSpeedTest(test: SpeedTestEntity) = Unit

    override fun observeSpeedTests(): Flow<List<SpeedTestEntity>> = emptyFlow()

    override suspend fun trimSpeedTests(keep: Int) = Unit
}
