package com.wickedcoder.wifilens.feature.analyze.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import com.wickedcoder.wifilens.feature.analyze.domain.ChannelPlan
import com.wickedcoder.wifilens.feature.analyze.domain.ConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.Insight
import com.wickedcoder.wifilens.feature.analyze.domain.InsightSeverity
import com.wickedcoder.wifilens.feature.analyze.domain.ScannedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.buildInsights
import com.wickedcoder.wifilens.feature.analyze.domain.overallSeverity
import com.wickedcoder.wifilens.feature.analyze.domain.planChannels
import com.wickedcoder.wifilens.feature.analyze.domain.toConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.toScannedNetworks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "InsightsViewModel"

/** The speed-test half of the one-tap health check. */
sealed interface HealthCheckState {
    data object Idle : HealthCheckState

    data class Running(val progress: Float) : HealthCheckState

    data class Done(val downloadMbps: Float) : HealthCheckState

    data object Failed : HealthCheckState
}

/** The Health tab: findings for the current connection, the channel plan for every band, and the check's progress. */
data class InsightsState(
    val connected: ConnectedNetwork? = null,
    val insights: List<Insight> = emptyList(),
    val overall: InsightSeverity = InsightSeverity.Good,
    val channelPlan: List<ChannelPlan> = emptyList(),
    val check: HealthCheckState = HealthCheckState.Idle,
)

/**
 * Network insights (Sprint 9), built offline from the scan and connection data the app already reads. The findings
 * update with every scan; "Run health check" adds a speed test (stored in speed-test history like any other).
 */
@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        scanRepository: WifiScanRepository,
        connectionRepository: WifiConnectionRepository,
        private val speedTestRepository: SpeedTestRepository,
        private val historyRepository: HistoryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val _state = MutableStateFlow(InsightsState())
        val state: StateFlow<InsightsState> = _state.asStateFlow()

        private var connection: WifiConnectionInfo.Connected? = null
        private var networks: List<ScannedNetwork> = emptyList()
        private var checkJob: Job? = null

        init {
            connectionRepository
                .observe()
                .onEach { info ->
                    connection = info as? WifiConnectionInfo.Connected
                    recompute()
                }.launchIn(viewModelScope)

            scanRepository
                .observe()
                .filterIsInstance<WifiScanUpdate.Results>()
                .onEach { update ->
                    networks = update.results.toScannedNetworks()
                    recompute()
                }.launchIn(viewModelScope)
        }

        fun runHealthCheck() {
            if (_state.value.check is HealthCheckState.Running || connection == null) return
            _state.update { it.copy(check = HealthCheckState.Running(0f)) }
            checkJob?.cancel()
            checkJob = viewModelScope.launch {
                try {
                    speedTestRepository.run().collect { update ->
                        val check = when (update) {
                            is SpeedTestUpdate.Running -> HealthCheckState.Running(update.fraction)
                            is SpeedTestUpdate.Finished -> HealthCheckState.Done(update.mbps).also { record(update.mbps) }
                            is SpeedTestUpdate.Failed -> HealthCheckState.Failed
                        }
                        _state.update { it.copy(check = check) }
                        recompute()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "health-check speed test failed", e)
                    _state.update { it.copy(check = HealthCheckState.Failed) }
                }
            }
        }

        private fun recompute() {
            val connected = connection?.toConnectedNetwork()
            val speed = (_state.value.check as? HealthCheckState.Done)?.downloadMbps
            val insights = buildInsights(connected, networks, speed)
            _state.update {
                it.copy(
                    connected = connected,
                    insights = insights,
                    overall = overallSeverity(insights),
                    channelPlan = planChannels(networks, connected),
                )
            }
        }

        private suspend fun record(mbps: Float) {
            val link = connection
            try {
                historyRepository.recordSpeedTest(
                    SpeedTestRecord(clock.nowMillis(), mbps, link?.linkSpeedMbps?.takeIf { it > 0 }, link?.rssi, link?.frequencyMhz),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "storing the speed test failed", e) // best-effort, the result is still shown
            }
        }
    }
