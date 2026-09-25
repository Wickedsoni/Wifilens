package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme

private data class PrivacyStat(val label: String, val value: String)

private val PRIVACY_STATS = listOf(
    PrivacyStat("Internet", "Speed test only"),
    PrivacyStat("Account", "None"),
    PrivacyStat("Ads", "None"),
    PrivacyStat("Tracking", "None"),
)

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WifiLensTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        BackHeader(title = "About", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = NothingSpacing.md)) {
            Text("WIFILENS", style = NothingType.displayMd, color = colors.textDisplay)
            Text(
                "Version 1.0",
                style = NothingType.caption,
                color = colors.textDisabled,
                modifier = Modifier.padding(top = NothingSpacing.xs, bottom = NothingSpacing.xl2),
            )
            NothingDivider()

            PRIVACY_STATS.forEach { stat -> MoreRow(label = stat.label.uppercase(), trailing = {
                Text(stat.value.uppercase(), style = NothingType.caption, color = colors.success)
            }) }

            MoreRow(label = "Open-source licenses", onClick = onOpenLicenses)
        }
    }
}
