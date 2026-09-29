package com.wickedcoder.wifilens.feature.diagnose.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.common.WhileUiSubscribed
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.toWifiBand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** How often the meter re-reads the connection, and how many readings its trace keeps (one minute). */
internal const val METER_PERIOD_MS = 1_000L
internal const val METER_HISTORY_SIZE = 60

/** The live signal meter: the connected access point's RSSI now, its last minute, and the session's best/worst. */
data class SignalMeterState(
    /** Null while not connected. */
    val rssi: Int? = null,
    /** Band label ("2.4", "5", "6") of the current connection. */
    val band: String? = null,
    val linkSpeedMbps: Int? = null,
    /** Oldest first; disconnected moments are skipped, not recorded. */
    val history: List<Int> = emptyList(),
    val best: Int? = null,
    val worst: Int? = null,
    /** Master haptics switch AND the confirm category: a tick when the signal changes quality tier. */
    val tierHaptics: Boolean = true,
)

/** Folds one connection reading into the meter. */
internal fun SignalMeterState.next(info: WifiConnectionInfo): SignalMeterState = when (info) {
    is WifiConnectionInfo.Connected -> copy(
        rssi = info.rssi,
        band = info.frequencyMhz.toBandLabel(),
        linkSpeedMbps = info.linkSpeedMbps.takeIf { it > 0 },
        history = (history + info.rssi).takeLast(METER_HISTORY_SIZE),
        best = maxOf(best ?: info.rssi, info.rssi),
        worst = minOf(worst ?: info.rssi, info.rssi),
    )
    WifiConnectionInfo.Disconnected -> copy(rssi = null, band = null, linkSpeedMbps = null)
}

private fun Int.toBandLabel(): String? = toWifiBand().takeIf { it != UNKNOWN_BAND }

private const val UNKNOWN_BAND = "?"

/**
 * Backs the Signal tab. The connection is polled only while the tab is on screen: [state] stops its upstream
 * [WhileUiSubscribed] after the tab leaves composition, and polling the connection never uses the scan quota.
 */
@HiltViewModel
class SignalMeterViewModel
    @Inject
    constructor(
        connectionRepository: WifiConnectionRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<SignalMeterState> =
            combine(
                connectionRepository.observeLive(METER_PERIOD_MS).scan(SignalMeterState()) { meter, info -> meter.next(info) },
                settingsRepository.settings,
            ) { meter, settings -> meter.copy(tierHaptics = settings.hapticsEnabled && settings.hapticConfirm) }
                .stateIn(viewModelScope, WhileUiSubscribed, SignalMeterState())
    }
