package com.wickedcoder.wifilens.feature.more.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing

private data class GlossaryTerm(
    @StringRes val term: Int,
    @StringRes val definition: Int,
)

private val GLOSSARY_TERMS = listOf(
    GlossaryTerm(R.string.more_glossary_rssi, R.string.more_glossary_rssi_def),
    GlossaryTerm(R.string.more_glossary_dbm, R.string.more_glossary_dbm_def),
    GlossaryTerm(R.string.more_glossary_ssid, R.string.more_glossary_ssid_def),
    GlossaryTerm(R.string.more_glossary_bssid, R.string.more_glossary_bssid_def),
    GlossaryTerm(R.string.more_glossary_band, R.string.more_glossary_band_def),
    GlossaryTerm(R.string.more_glossary_channel, R.string.more_glossary_channel_def),
    GlossaryTerm(R.string.more_glossary_channel_width, R.string.more_glossary_channel_width_def),
    GlossaryTerm(R.string.more_glossary_co_channel, R.string.more_glossary_co_channel_def),
    GlossaryTerm(R.string.more_glossary_congestion, R.string.more_glossary_congestion_def),
    GlossaryTerm(R.string.more_glossary_path_loss, R.string.more_glossary_path_loss_def),
    GlossaryTerm(R.string.more_glossary_path_loss_exponent, R.string.more_glossary_path_loss_exponent_def),
    GlossaryTerm(R.string.more_glossary_wall_attenuation, R.string.more_glossary_wall_attenuation_def),
    GlossaryTerm(R.string.more_glossary_measured_predicted, R.string.more_glossary_measured_predicted_def),
)

@Composable
fun GlossaryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    // Resolved first so search matches the text in the current language.
    val resolved = GLOSSARY_TERMS.map { stringResource(it.term) to stringResource(it.definition) }
    val filtered = remember(query, resolved) {
        resolved
            .filter { (term, definition) -> term.contains(query, ignoreCase = true) || definition.contains(query, ignoreCase = true) }
            .sortedBy { it.first }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        BackHeader(title = stringResource(R.string.more_glossary), onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.more_glossary_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md),
        )

        LazyColumn(contentPadding = PaddingValues(WifiLensSpacing.md)) {
            items(filtered, key = { it.first }) { (term, definition) ->
                Column {
                    Text(term, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Text(
                        definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = WifiLensSpacing.xs, bottom = WifiLensSpacing.sm),
                    )
                    WifiLensDivider()
                }
            }
        }
    }
}
