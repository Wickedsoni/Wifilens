package com.wickedcoder.wifilens.feature.diagnose.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.common.WhileUiSubscribed
import com.wickedcoder.wifilens.core.designsystem.WifiLensBarChart
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import com.wickedcoder.wifilens.feature.diagnose.domain.slowestHour
import com.wickedcoder.wifilens.feature.diagnose.domain.speedByHourOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** Recent tests listed under the chart; the chart itself uses every stored test. */
private const val RECENT_TESTS_SHOWN = 10

/** Speed-test history for the Speed tab: every test, its hour-of-day averages and the slow hour, if any. */
data class SpeedHistoryState(
    val tests: List<SpeedTestRecord> = emptyList(),
    val byHour: List<Float?> = emptyList(),
    val slowHour: Int? = null,
)

@HiltViewModel
class SpeedHistoryViewModel
    @Inject
    constructor(
        history: HistoryRepository,
    ) : ViewModel() {
        val state: StateFlow<SpeedHistoryState> =
            history
                .observeSpeedTests()
                .map { tests ->
                    val zone = ZoneId.systemDefault()
                    SpeedHistoryState(tests, speedByHourOfDay(tests, zone), slowestHour(tests, zone))
                }.stateIn(viewModelScope, WhileUiSubscribed, SpeedHistoryState())
    }

@Composable
internal fun SpeedHistorySection(viewModel: SpeedHistoryViewModel = hiltViewModel()) {
    val history by viewModel.state.collectAsStateWithLifecycle()
    SpeedHistoryContent(history)
}

@Composable
internal fun SpeedHistoryContent(history: SpeedHistoryState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
        WifiLensLabel(stringResource(R.string.diagnose_history_title), modifier = Modifier.semantics { heading() })
        if (history.tests.isEmpty()) {
            Text(
                stringResource(R.string.diagnose_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            pluralStringResource(R.plurals.diagnose_history_count, history.tests.size, history.tests.size),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        WifiLensBarChart(
            values = history.byHour,
            description = stringResource(R.string.diagnose_history_chart_description),
            modifier = Modifier.fillMaxWidth().height(96.dp),
            barColor = { hour, _ -> if (hour == history.slowHour) colors.danger else colors.primary },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach { hour ->
                Text(
                    stringResource(R.string.diagnose_history_hour, hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        history.slowHour?.let { hour ->
            Text(
                stringResource(R.string.diagnose_history_slow_hour, hour),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
        }
        WifiLensDivider()
        val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
        history.tests.take(RECENT_TESTS_SHOWN).forEach { test ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.xs).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatter.format(Instant.ofEpochMilli(test.timestampMillis).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.diagnose_history_mbps, test.downloadMbps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
