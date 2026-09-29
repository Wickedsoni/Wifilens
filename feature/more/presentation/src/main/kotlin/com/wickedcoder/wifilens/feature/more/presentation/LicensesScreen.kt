package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing

private data class License(val name: String, val terms: String)

// Third-party components that ship in the app (B-39: kept in sync with gradle/libs.versions.toml). Proper nouns,
// so they're data rather than translatable strings.
private val LICENSES = listOf(
    License("AndroidX (Activity, Core, Lifecycle, Navigation, DataStore, Splash screen)", "Apache License 2.0"),
    License("Jetpack Compose and Material 3", "Apache License 2.0"),
    License("Room", "Apache License 2.0"),
    License("Dagger and Hilt", "Apache License 2.0"),
    License("Kotlin, kotlinx.coroutines and kotlinx.serialization", "Apache License 2.0"),
    License("javax.inject", "Apache License 2.0"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        BackHeader(title = stringResource(R.string.more_licenses_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(WifiLensSpacing.md)) {
            items(LICENSES, key = { it.name }) { license ->
                Column {
                    Text(license.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(
                        license.terms,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = WifiLensSpacing.xs, bottom = WifiLensSpacing.sm),
                    )
                    WifiLensDivider()
                }
            }
        }
    }
}
