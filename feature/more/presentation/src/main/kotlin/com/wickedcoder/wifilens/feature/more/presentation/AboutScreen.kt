package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.success

private data class PrivacyStat(val label: String, val value: String)

private val PRIVACY_STATS = listOf(
    PrivacyStat("Internet", "Speed test only"),
    PrivacyStat("Account", "None"),
    PrivacyStat("Ads", "None"),
    PrivacyStat("Tracking", "None"),
)

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        BackHeader(title = "About", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = WifiLensSpacing.md)) {
            Text("WIFILENS", style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
            Text(
                "Version 1.0",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = WifiLensSpacing.xs, bottom = WifiLensSpacing.xl2),
            )
            WifiLensDivider()

            PRIVACY_STATS.forEach { stat ->
                MoreRow(label = stat.label.uppercase(), trailing = {
                    Text(stat.value.uppercase(), style = MaterialTheme.typography.bodySmall, color = colors.success)
                })
            }

            MoreRow(label = "Open-source licenses", onClick = onOpenLicenses)
        }
    }
}
