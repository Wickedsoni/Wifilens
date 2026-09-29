package com.wickedcoder.wifilens.feature.diagnose.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.common.DefaultDispatcher
import com.wickedcoder.wifilens.core.designsystem.UiText
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.feature.diagnose.domain.AnalyzeCoverage
import com.wickedcoder.wifilens.feature.diagnose.domain.FindBestRouterSpot
import com.wickedcoder.wifilens.feature.diagnose.domain.MoveRouter
import com.wickedcoder.wifilens.feature.diagnose.domain.ObservePlanContext
import com.wickedcoder.wifilens.feature.diagnose.domain.PlanContext
import com.wickedcoder.wifilens.feature.diagnose.domain.calibratedBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "DiagnoseOptimizer"

/**
 * MVI ViewModel for the Diagnose tab. It owns the screen state and orchestrates; the RF maths and the
 * storage live behind the domain use-cases ([AnalyzeCoverage], [FindBestRouterSpot], [MoveRouter],
 * [ObservePlanContext]), so nothing here touches Room or does signal-strength arithmetic.
 */
@HiltViewModel
class DiagnoseViewModel
    @Inject
    constructor(
        private val observePlanContext: ObservePlanContext,
        private val settingsRepository: SettingsRepository,
        private val connectionRepository: WifiConnectionRepository,
        private val speedTestRepository: SpeedTestRepository,
        private val analyzeCoverage: AnalyzeCoverage,
        private val findBestRouterSpot: FindBestRouterSpot,
        private val moveRouter: MoveRouter,
        private val historyRepository: HistoryRepository,
        private val clock: Clock,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DiagnoseState())
        val state: StateFlow<DiagnoseState> = _state.asStateFlow()

        private var optimizerJob: Job? = null
        private var speedTestJob: Job? = null

        /** Plan, router, pins and calibration as of the last emission; a change invalidates any finished optimizer run. */
        private var lastWorld: World? = null

        /** Kept in sync from [settingsRepository] (with the plan's calibration applied) so action handlers
         * (RunOptimizer) that run outside the init{} collector use the same model as the coverage map. */
        private var settings: AppSettings = AppSettings()

        /** The connection as of the last change, stored with each speed test ("95 Mbps at −52 dBm on 5 GHz"). */
        private var lastConnection: WifiConnectionInfo.Connected? = null

        init {
            combine(observePlanContext(), settingsRepository.settings) { context, appSettings -> context to appSettings }
                .onEach { (context, userSettings) ->
                    val appSettings = userSettings.calibratedBy(context.calibration)
                    settings = appSettings
                    val world = World(context.plan, context.routerPos, context.devicePins, context.calibration)
                    // "Best spot" results describe the plan/router/pins they were computed for. Once any of
                    // those change (e.g. after "Move router here") they are stale, so drop them rather than
                    // keep offering a move that has already happened.
                    val staleOptimizer = lastWorld != null && lastWorld != world
                    lastWorld = world
                    if (staleOptimizer) {
                        optimizerJob?.cancel()
                        optimizerJob = null
                    }
                    _state.update {
                        val updated = it.copy(
                            plan = context.plan,
                            routerPos = context.routerPos,
                            devicePins = context.devicePins,
                            calibration = context.calibration,
                        )
                        if (staleOptimizer) {
                            updated.copy(
                                optimizerState = OptimizerState.Idle,
                                bestTile = null,
                                bestTileGainDb = null,
                                tileScores = emptyMap(),
                            )
                        } else {
                            updated
                        }
                    }
                    recomputeCoverage(context, appSettings)
                }.launchIn(viewModelScope)

            connectionRepository
                .observe()
                .onEach { info ->
                    lastConnection = info as? WifiConnectionInfo.Connected
                    _state.update {
                        when (info) {
                            is WifiConnectionInfo.Connected -> {
                                it.copy(isOnWifi = true, linkSpeedMbps = info.linkSpeedMbps.takeIf { mbps -> mbps > 0 })
                            }
                            WifiConnectionInfo.Disconnected -> {
                                it.copy(isOnWifi = false, linkSpeedMbps = null)
                            }
                        }
                    }
                }.launchIn(viewModelScope)
        }

        fun onAction(action: DiagnoseAction) {
            when (action) {
                DiagnoseAction.TabCoverage -> _state.update { it.copy(tab = DiagnoseTab.Coverage) }
                DiagnoseAction.TabBestSpot -> _state.update { it.copy(tab = DiagnoseTab.BestSpot) }
                DiagnoseAction.TabSpeed -> _state.update { it.copy(tab = DiagnoseTab.Speed) }
                DiagnoseAction.TabSignal -> _state.update { it.copy(tab = DiagnoseTab.Signal) }
                DiagnoseAction.RunOptimizer -> runOptimizer()
                DiagnoseAction.RunSpeedTest -> runSpeedTest()
                DiagnoseAction.DismissError -> _state.update { it.copy(errorMessage = null) }
                is DiagnoseAction.MoveRouter -> moveRouterTo(action.pos)
            }
        }

        private suspend fun recomputeCoverage(context: PlanContext, appSettings: AppSettings) {
            if (context.plan == null || context.routerPos == null) {
                _state.update {
                    it.copy(coverage = emptyList(), worstDevice = null, roomSummaries = emptyList(), findings = emptyList())
                }
                return
            }

            _state.update { it.copy(isComputingCoverage = true) }
            val report = withContext(defaultDispatcher) { analyzeCoverage(context, appSettings) }
            _state.update {
                it.copy(
                    coverage = report.coverage,
                    worstDevice = report.worstDevice,
                    roomSummaries = report.roomSummaries,
                    findings = report.findings,
                    isComputingCoverage = false,
                )
            }
        }

        private fun runOptimizer() {
            val plan = _state.value.plan ?: return
            val devices = _state.value.devicePins
            val currentRouter = _state.value.routerPos
            if (devices.isEmpty()) return
            val appSettings = settings

            optimizerJob?.cancel()
            optimizerJob = viewModelScope.launch {
                _state.update { it.copy(optimizerState = OptimizerState.Running(0f)) }

                val startMs = System.currentTimeMillis()
                val best = withContext(defaultDispatcher) {
                    findBestRouterSpot(plan, devices, currentRouter, appSettings) { progress ->
                        _state.update { it.copy(optimizerState = OptimizerState.Running(progress)) }
                    }
                } ?: return@launch
                Log.d(TAG, "evaluated ${best.scores.size} tiles x ${devices.size} devices in ${System.currentTimeMillis() - startMs}ms")

                _state.update {
                    it.copy(
                        optimizerState = if (best.alreadyOptimal) OptimizerState.AlreadyOptimal else OptimizerState.Complete,
                        bestTile = best.bestTile,
                        bestTileGainDb = best.gainDb,
                        tileScores = best.scores,
                    )
                }
            }
        }

        private fun runSpeedTest() {
            if (_state.value.speedTest is SpeedTestState.Running) return
            if (!_state.value.isOnWifi) {
                _state.update { it.copy(speedTest = SpeedTestState.Failed(UiText.Resource(R.string.diagnose_speed_needs_wifi))) }
                return
            }

            // Keep the last completed result so the UI can show "vs last test" once this run finishes.
            val previous = (_state.value.speedTest as? SpeedTestState.Finished)?.mbps ?: _state.value.previousSpeedMbps
            _state.update { it.copy(speedTest = SpeedTestState.Running(mbps = 0f, progress = 0f), previousSpeedMbps = previous) }

            speedTestJob?.cancel()
            speedTestJob = viewModelScope.launch {
                try {
                    speedTestRepository.run().collect { update ->
                        _state.update {
                            it.copy(
                                speedTest = when (update) {
                                    is SpeedTestUpdate.Running -> {
                                        SpeedTestState.Running(update.mbps, update.fraction)
                                    }
                                    is SpeedTestUpdate.Finished -> {
                                        SpeedTestState
                                            .Finished(
                                                update.mbps,
                                            ).also { recordSpeedTest(update.mbps) }
                                    }
                                    is SpeedTestUpdate.Failed -> {
                                        SpeedTestState.Failed(UiText.Resource(R.string.diagnose_speed_failed))
                                    }
                                },
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // must not escape viewModelScope and crash the app, and must not leave the UI stuck on "Testing".
                    // The user sees a generic message; the cause goes to the log.
                    Log.w(TAG, "speed test failed", e)
                    _state.update { it.copy(speedTest = SpeedTestState.Failed(UiText.Resource(R.string.diagnose_speed_failed))) }
                }
            }
        }

        private fun recordSpeedTest(mbps: Float) {
            val connection = lastConnection
            val record = SpeedTestRecord(
                timestampMillis = clock.nowMillis(),
                downloadMbps = mbps,
                linkSpeedMbps = connection?.linkSpeedMbps?.takeIf { it > 0 },
                rssi = connection?.rssi,
                frequencyMhz = connection?.frequencyMhz,
            )
            viewModelScope.launch {
                try {
                    historyRepository.recordSpeedTest(record)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "storing the speed test failed", e) // history is best-effort; the result is still shown
                }
            }
        }

        private fun moveRouterTo(pos: Vec2) {
            viewModelScope.launch {
                try {
                    // The new position flows back through the plan-context collector in init{}, which
                    // re-triggers the coverage computation.
                    moveRouter(pos)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // a raw SQLiteException must not escape viewModelScope and crash the app
                    Log.w(TAG, "moving the router failed", e)
                    _state.update { it.copy(errorMessage = UiText.Resource(R.string.diagnose_error_move_router)) }
                }
            }
        }
    }

/** What a finished optimizer run was computed for. */
private data class World(
    val plan: GridPlan?,
    val routerPos: Vec2?,
    val devicePins: List<DevicePin>,
    val calibration: PlanCalibration?,
)
