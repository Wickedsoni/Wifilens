package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLogo
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
            WifiLensLogo(modifier = Modifier.padding(bottom = WifiLensSpacing.md))
            Text("WifiLens", style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
            Text(
                "Version ${appVersionName()}",
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

/** The installed versionName (feature modules can't see the app's BuildConfig). */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
