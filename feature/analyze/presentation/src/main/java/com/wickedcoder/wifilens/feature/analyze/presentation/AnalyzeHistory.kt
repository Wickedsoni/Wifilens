@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // flatMapLatest

package com.wickedcoder.wifilens.feature.analyze.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.common.WhileUiSubscribed
import com.wickedcoder.wifilens.core.designsystem.ChartPoint
import com.wickedcoder.wifilens.core.designsystem.FAIR_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.GOOD_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.WifiLensBarChart
import com.wickedcoder.wifilens.core.designsystem.WifiLensBottomSheet
import com.wickedcoder.wifilens.core.designsystem.WifiLensCard
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensLineChart
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.signalColor
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.SAMPLE_RETENTION_DAYS
import com.wickedcoder.wifilens.core.model.SignalPoint
import com.wickedcoder.wifilens.feature.analyze.domain.ScannedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.congestionByHour
import com.wickedcoder.wifilens.feature.analyze.domain.hourStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The signal-history sheet shows the last day; the busy-hours chart the last 24 clock hours. */
private val HISTORY_WINDOW_MS = TimeUnit.HOURS.toMillis(24)
internal const val BUSY_HOURS = 24

/** Signal trace y-axis, the same scale as the Diagnose meter. */
private const val TRACE_MIN_DBM = -95f
private const val TRACE_MAX_DBM = -30f

/** One network's recorded signal for the history sheet. */
data class NetworkHistoryState(
    val network: ScannedNetwork,
    val points: List<SignalPoint>,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
)

/** Recorded history for the Analyze tab: a tapped network's signal, and the selected band's busy hours. */
@HiltViewModel
class AnalyzeHistoryViewModel
    @Inject
    constructor(
        private val history: HistoryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val selected = MutableStateFlow<ScannedNetwork?>(null)
        private val band = MutableStateFlow<String?>(null)

        val networkHistory: StateFlow<NetworkHistoryState?> =
            selected
                .flatMapLatest { network ->
                    if (network == null) return@flatMapLatest flowOf(null)
                    val end = clock.nowMillis()
                    val start = end - HISTORY_WINDOW_MS
                    history.observeSignal(network.id, start).map { NetworkHistoryState(network, it, start, end) }
                }.stateIn(viewModelScope, WhileUiSubscribed, null)

        /** Congestion per hour, oldest first, for the band shown on the Spectrum tab. */
        val busyHours: StateFlow<List<Float?>> =
            band
                .flatMapLatest { band ->
                    if (band == null) return@flatMapLatest flowOf(emptyList())
                    val first = hourStart(clock.nowMillis()) - TimeUnit.HOURS.toMillis(BUSY_HOURS - 1L)
                    history.observeCongestion(band, first).map { congestionByHour(it, first, BUSY_HOURS) }
                }.stateIn(viewModelScope, WhileUiSubscribed, emptyList())

        fun showHistory(network: ScannedNetwork) {
            selected.value = network
        }

        fun dismissHistory() {
            selected.value = null
        }

        fun onSpectrumBand(bandLabel: String) {
            band.value = bandLabel
        }
    }

@Composable
// WifiLensBottomSheet's sheetState default (rememberModalBottomSheetState()) is inlined at the call site.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun NetworkHistorySheet(state: NetworkHistoryState, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
        ) {
            Text(state.network.ssid, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Text(
                stringResource(R.string.analyze_history_subtitle, state.network.bssidMasked),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            WifiLensLineChart(
                points = state.points.map { ChartPoint(it.timestampMillis.toFloat(), it.rssi.toFloat()) },
                xRange = state.windowStartMillis.toFloat()..state.windowEndMillis.toFloat(),
                yRange = TRACE_MIN_DBM..TRACE_MAX_DBM,
                description = stringResource(R.string.analyze_history_chart_description),
                guides = listOf(GOOD_RSSI_DBM, FAIR_RSSI_DBM),
                guideColor = { colors.signalColor(it) },
                colorFor = { colors.signalColor(it) },
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.analyze_history_axis_start),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.analyze_history_axis_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (state.points.isEmpty()) {
                Text(
                    stringResource(R.string.analyze_history_empty, SAMPLE_RETENTION_DAYS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                val values = state.points.map { it.rssi }
                Text(
                    stringResource(R.string.analyze_history_stats, values.max(), values.average().toInt(), values.min()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}

/** "Busy hours" on the Spectrum tab: the band's total congestion per hour over the last day. */
@Composable
internal fun BusyHoursCard(hours: List<Float?>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    WifiLensCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(WifiLensSpacing.md), verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            WifiLensLabel(stringResource(R.string.analyze_busy_hours_title))
            if (hours.all { it == null }) {
                Text(
                    stringResource(R.string.analyze_busy_hours_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                WifiLensBarChart(
                    values = hours,
                    description = stringResource(R.string.analyze_busy_hours_description),
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.analyze_history_axis_start),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.analyze_history_axis_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
