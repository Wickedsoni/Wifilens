package com.wickedcoder.wifilens.feature.more.presentation

import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLogo
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.success

private data class PrivacyStat(
    @StringRes val label: Int,
    @StringRes val value: Int,
)

private val PRIVACY_STATS = listOf(
    PrivacyStat(R.string.more_about_internet, R.string.more_about_internet_value),
    PrivacyStat(R.string.more_about_account, R.string.more_about_none),
    PrivacyStat(R.string.more_about_ads, R.string.more_about_none),
    PrivacyStat(R.string.more_about_tracking, R.string.more_about_none),
)

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.more_about_privacy_url)
    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        BackHeader(title = stringResource(R.string.more_about), onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = WifiLensSpacing.md)) {
            WifiLensLogo(modifier = Modifier.padding(bottom = WifiLensSpacing.md))
            Text(stringResource(R.string.more_title), style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
            Text(
                stringResource(R.string.more_about_version, appVersionName()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = WifiLensSpacing.xs, bottom = WifiLensSpacing.xl2),
            )
            WifiLensDivider()

            PRIVACY_STATS.forEach { stat ->
                MoreRow(label = stringResource(stat.label), trailing = {
                    Text(stringResource(stat.value), style = MaterialTheme.typography.bodySmall, color = colors.success)
                })
            }

            MoreRow(label = stringResource(R.string.more_about_privacy), onClick = { uriHandler.openUri(privacyUrl) })
            MoreRow(label = stringResource(R.string.more_about_licenses), onClick = onOpenLicenses)
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
