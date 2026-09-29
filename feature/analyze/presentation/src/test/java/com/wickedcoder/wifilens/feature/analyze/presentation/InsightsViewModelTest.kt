package com.wickedcoder.wifilens.feature.analyze.presentation

import com.wickedcoder.wifilens.core.model.ChannelCongestion
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.SignalPoint
import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import com.wickedcoder.wifilens.core.testing.MainDispatcherRule
import com.wickedcoder.wifilens.feature.analyze.domain.Insight
import com.wickedcoder.wifilens.feature.analyze.domain.InsightSeverity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InsightsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val connection =
        MutableStateFlow<WifiConnectionInfo>(WifiConnectionInfo.Connected("\"Home\"", -55, 866, 5180, "aa:bb:cc:dd:ee:01"))
    private val scan = MutableStateFlow<WifiScanUpdate>(
        WifiScanUpdate.Results(
            listOf(WifiScanResult("Home", "aa:bb:cc:dd:ee:01", -55, 5180, "[RSN-PSK+SAE-CCMP-128][ESS]")),
            0L,
            fresh = true,
        ),
    )
    private val history = RecordingHistory()
    private var speed: Flow<SpeedTestUpdate> = flowOf(SpeedTestUpdate.Finished(3f))

    private fun viewModel() = InsightsViewModel(
        scanRepository = object : WifiScanRepository {
            override fun observe(): Flow<WifiScanUpdate> = scan

            override fun startScan() = true

            override fun currentUpdate(): WifiScanUpdate = scan.value
        },
        connectionRepository = object : WifiConnectionRepository {
            override fun observe(): Flow<WifiConnectionInfo> = connection
        },
        speedTestRepository = object : SpeedTestRepository {
            override fun run(): Flow<SpeedTestUpdate> = speed
        },
        historyRepository = history,
        clock = { 42L },
    )

    @Test
    fun `findings follow the scan and connection`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val insights = vm.state.value.insights
        assertTrue(insights.any { it is Insight.Signal && it.severity == InsightSeverity.Good })
        assertTrue(insights.any { it is Insight.Security })
        assertEquals(
            "5",
            vm.state.value.channelPlan
                .single()
                .band,
        )
    }

    @Test
    fun `the health check adds the speed test and stores it in history`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.runHealthCheck()
        advanceUntilIdle()

        assertEquals(HealthCheckState.Done(3f), vm.state.value.check)
        assertEquals(InsightSeverity.Problem, vm.state.value.overall) // 3 Mbps is very slow
        assertEquals(listOf(SpeedTestRecord(42L, 3f, 866, -55, 5180)), history.tests)
    }

    @Test
    fun `nothing to check while disconnected`() = runTest(mainDispatcherRule.testDispatcher) {
        connection.value = WifiConnectionInfo.Disconnected
        val vm = viewModel()
        advanceUntilIdle()

        vm.runHealthCheck()
        advanceUntilIdle()

        assertTrue(
            vm.state.value.insights
                .isEmpty(),
        )
        assertEquals(HealthCheckState.Idle, vm.state.value.check)
    }
}

private class RecordingHistory : HistoryRepository {
    val tests = mutableListOf<SpeedTestRecord>()

    override fun observeSignal(bssid: String, sinceMillis: Long): Flow<List<SignalPoint>> = flowOf(emptyList())

    override fun observeCongestion(band: String, sinceMillis: Long): Flow<List<ChannelCongestion>> = flowOf(emptyList())

    override fun observeSpeedTests(): Flow<List<SpeedTestRecord>> = flowOf(tests)

    override suspend fun recordSpeedTest(record: SpeedTestRecord) {
        tests += record
    }

    override suspend fun recordScan(results: List<WifiScanResult>, timestampMillis: Long) = Unit

    override suspend fun prune(nowMillis: Long) = Unit
}
