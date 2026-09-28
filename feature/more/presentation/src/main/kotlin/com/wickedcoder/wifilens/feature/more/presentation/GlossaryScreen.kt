package com.wickedcoder.wifilens.feature.more.presentation

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
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing

private data class GlossaryTerm(val term: String, val definition: String)

private val GLOSSARY_TERMS = listOf(
    GlossaryTerm("RSSI", "Received Signal Strength Indicator — how strong a Wi-Fi signal is, in dBm."),
    GlossaryTerm("dBm", "Decibel-milliwatts — the unit RSSI is measured in. Closer to 0 is stronger."),
    GlossaryTerm("SSID", "The network name you see when choosing a Wi-Fi network."),
    GlossaryTerm("BSSID", "The MAC address of a specific access point radio, unique per band."),
    GlossaryTerm("Band", "A frequency range Wi-Fi operates in — 2.4 GHz, 5 GHz, or 6 GHz."),
    GlossaryTerm("Channel", "A slice of a band that a network transmits on."),
    GlossaryTerm("Channel width", "How much spectrum a channel uses — wider is faster but more crowded."),
    GlossaryTerm("Co-channel interference", "Slowdown from another network using the exact same channel."),
    GlossaryTerm("Congestion score", "How crowded a channel is, 0 (clear) to 100 (crowded)."),
    GlossaryTerm("Path loss", "Signal strength lost as it travels from router to device."),
    GlossaryTerm("Path-loss exponent", "How fast signal fades with distance — higher means denser obstacles."),
    GlossaryTerm("Wall attenuation", "Signal loss caused by passing through a wall, by material."),
    GlossaryTerm("Measured vs predicted", "Measured comes from a live scan; predicted comes from your floor plan model."),
)

@Composable
fun GlossaryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        GLOSSARY_TERMS
            .filter { it.term.contains(query, ignoreCase = true) || it.definition.contains(query, ignoreCase = true) }
            .sortedBy { it.term }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        BackHeader(title = "Glossary", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md),
        )

        LazyColumn(contentPadding = PaddingValues(WifiLensSpacing.md)) {
            items(filtered, key = { it.term }) { entry ->
                Column {
                    Text(entry.term.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                    Text(
                        entry.definition,
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
