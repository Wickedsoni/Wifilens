@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // flatMapLatest

package com.wickedcoder.wifilens.feature.map.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.common.WhileUiSubscribed
import com.wickedcoder.wifilens.core.designsystem.UiText
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Measurement
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.rf.CalibrationProblem
import com.wickedcoder.wifilens.core.rf.CalibrationResult
import com.wickedcoder.wifilens.core.rf.CalibrationSample
import com.wickedcoder.wifilens.core.rf.MIN_CALIBRATION_SAMPLES
import com.wickedcoder.wifilens.core.rf.fitPathLoss
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Readings averaged into one measurement, and how often they're taken: about three seconds per tile. */
internal const val SAMPLES_PER_READING = 6
internal const val SAMPLE_PERIOD_MS = 500L

/** Gives up on a tile if Wi-Fi drops for this long mid-reading; whatever was sampled still counts. */
private const val READING_TIMEOUT_MS = 8_000L

private const val TAG = "SurveyViewModel"

/** Walk-survey state for the Map's Measure tool. */
data class SurveyUiState(
    val planId: Long? = null,
    val measurements: List<Measurement> = emptyList(),
    val calibration: PlanCalibration? = null,
    /** The tile being measured right now, and how far along (0..1). */
    val measuringAt: Vec2? = null,
    val progress: Float = 0f,
    /** Bumped each time a reading is saved, so the UI can confirm it (haptic) exactly once. */
    val completedReadings: Int = 0,
    val infoMessage: UiText? = null,
    val errorMessage: UiText? = null,
) {
    val canCalibrate: Boolean get() = measurements.size >= MIN_CALIBRATION_SAMPLES && measuringAt == null
}

sealed interface SurveyAction {
    data class Measure(val pos: Vec2) : SurveyAction

    data object Calibrate : SurveyAction

    data object ClearMeasurements : SurveyAction

    data object DismissInfo : SurveyAction

    data object DismissError : SurveyAction
}

/**
 * Records measured signal on plan tiles and fits the plan's path-loss model from them (Sprint 7). The reading is
 * the connected access point's RSSI: reading the connection doesn't count against Android's Wi-Fi scan limit, so a
 * user can measure tile after tile without being throttled.
 */
@HiltViewModel
class SurveyViewModel
    @Inject
    constructor(
        private val mapRepository: MapRepository,
        private val surveyRepository: SurveyRepository,
        private val connection: WifiConnectionRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SurveyUiState())
        val state: StateFlow<SurveyUiState> = _state.asStateFlow()

        /** Live connected RSSI, polled only while someone is watching (the Measure strip is on screen). */
        val liveRssi: StateFlow<Int?> =
            connection
                .observeLive(SAMPLE_PERIOD_MS * 2)
                .map { (it as? WifiConnectionInfo.Connected)?.rssi }
                .stateIn(viewModelScope, WhileUiSubscribed, null)

        private var plan: GridPlan? = null
        private var routerPos: Vec2? = null

        init {
            mapRepository
                .observePlan()
                .flatMapLatest { snapshot ->
                    plan = snapshot.plan
                    val planId = snapshot.planId ?: return@flatMapLatest flowOf(SurveyUiState())
                    surveyRepository.observeMeasurements(planId).map { measurements ->
                        SurveyUiState(planId = planId, measurements = measurements, calibration = snapshot.calibration)
                    }
                }.onEach { loaded ->
                    _state.update {
                        it.copy(planId = loaded.planId, measurements = loaded.measurements, calibration = loaded.calibration)
                    }
                }.launchIn(viewModelScope)

            mapRepository
                .getRouterPin()
                .onEach { routerPos = it }
                .launchIn(viewModelScope)
        }

        fun onAction(action: SurveyAction) {
            when (action) {
                is SurveyAction.Measure -> measure(action.pos)
                SurveyAction.Calibrate -> calibrate()
                SurveyAction.ClearMeasurements -> clear()
                SurveyAction.DismissInfo -> _state.update { it.copy(infoMessage = null) }
                SurveyAction.DismissError -> _state.update { it.copy(errorMessage = null) }
            }
        }

        private fun measure(pos: Vec2) {
            val planId = _state.value.planId ?: return
            if (_state.value.measuringAt != null) return // one tile at a time
            _state.update { it.copy(measuringAt = pos, progress = 0f, infoMessage = null, errorMessage = null) }
            viewModelScope.launch {
                try {
                    val samples = sampleRssi(connection.observeLive(SAMPLE_PERIOD_MS), SAMPLES_PER_READING, READING_TIMEOUT_MS) { done ->
                        _state.update { it.copy(progress = done.toFloat() / SAMPLES_PER_READING) }
                    }
                    if (samples.rssi.isEmpty()) {
                        _state.update { it.copy(errorMessage = UiText.Resource(R.string.map_survey_error_no_wifi)) }
                    } else {
                        surveyRepository.addReading(planId, pos, samples.bssid, samples.rssi.average().toFloat(), samples.rssi.size)
                        _state.update { it.copy(completedReadings = it.completedReadings + 1) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "survey reading failed", e)
                    _state.update { it.copy(errorMessage = UiText.Resource(R.string.map_survey_error_save)) }
                } finally {
                    _state.update { it.copy(measuringAt = null, progress = 0f) }
                }
            }
        }

        private fun calibrate() {
            val current = _state.value
            val planId = current.planId ?: return
            val plan = plan ?: return
            val router = routerPos
            if (router == null) {
                _state.update { it.copy(errorMessage = UiText.Resource(R.string.map_survey_error_no_router)) }
                return
            }
            val samples = current.measurements.map { CalibrationSample(it.pos, it.rssi) }
            when (val result = fitPathLoss(plan, router, samples)) {
                is CalibrationResult.Rejected -> _state.update { it.copy(errorMessage = result.problem.toUiText()) }
                is CalibrationResult.Fitted -> launchStorage {
                    val fit = result.fit
                    surveyRepository.setCalibration(planId, PlanCalibration(fit.referenceRssiAt1m, fit.pathLossExponent, fit.rmseDb))
                    _state.update {
                        it.copy(infoMessage = UiText.Resource(R.string.map_survey_calibrated, listOf(fit.pathLossExponent, fit.rmseDb)))
                    }
                }
            }
        }

        private fun clear() {
            val planId = _state.value.planId ?: return
            launchStorage {
                surveyRepository.clearMeasurements(planId)
                _state.update { it.copy(infoMessage = UiText.Resource(R.string.map_survey_cleared)) }
            }
        }

        private fun launchStorage(block: suspend () -> Unit) {
            viewModelScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "survey storage operation failed", e)
                    _state.update { it.copy(errorMessage = UiText.Resource(R.string.map_survey_error_save)) }
                }
            }
        }
    }

/** What one reading collected: the RSSI samples (dBm) and the access point they came from, if Android shows it. */
internal data class SampledReading(val rssi: List<Int>, val bssid: String?)

/**
 * Takes up to [count] RSSI samples from [readings], skipping moments without a connection, and stops early after
 * [timeoutMs] with whatever it has. With no connection at all it gives up after [giveUpAfterMisses] readings
 * instead of waiting out the timeout. [onSample] reports progress (samples so far).
 */
internal suspend fun sampleRssi(
    readings: Flow<WifiConnectionInfo>,
    count: Int,
    timeoutMs: Long,
    giveUpAfterMisses: Int = 2,
    onSample: (Int) -> Unit = {},
): SampledReading {
    val rssi = mutableListOf<Int>()
    var bssid: String? = null
    var misses = 0
    withTimeoutOrNull(timeoutMs) {
        readings
            .takeWhile { info ->
                if (info !is WifiConnectionInfo.Connected) misses++
                rssi.isNotEmpty() || misses < giveUpAfterMisses
            }.filterIsInstance<WifiConnectionInfo.Connected>()
            .take(count)
            .collect { info ->
                rssi += info.rssi
                bssid = bssid ?: info.bssid
                onSample(rssi.size)
            }
    }
    return SampledReading(rssi, bssid)
}

private fun CalibrationProblem.toUiText(): UiText = when (this) {
    CalibrationProblem.TooFewSamples -> UiText.Resource(R.string.map_survey_error_too_few, listOf(MIN_CALIBRATION_SAMPLES))
    CalibrationProblem.TooLittleSpread -> UiText.Resource(R.string.map_survey_error_spread)
}
