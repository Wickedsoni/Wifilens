package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme

private data class License(val name: String, val terms: String)

private val LICENSES = listOf(
    License("AndroidX", "Apache License 2.0"),
    License("Jetpack Compose", "Apache License 2.0"),
    License("Koin", "Apache License 2.0"),
    License("Room", "Apache License 2.0"),
    License("Space Grotesk + Space Mono", "SIL Open Font License 1.1"),
    License("Doto", "SIL Open Font License 1.1"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WifiLensTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        BackHeader(title = "Licenses", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(NothingSpacing.md)) {
            items(LICENSES, key = { it.name }) { license ->
                Column {
                    Text(license.name, style = NothingType.body, color = colors.textPrimary)
                    Text(
                        license.terms,
                        style = NothingType.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = NothingSpacing.xs, bottom = NothingSpacing.sm),
                    )
                    NothingDivider()
                }
            }
        }
    }
}
