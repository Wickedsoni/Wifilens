package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme

private sealed interface MoreRoute {
    data object Root : MoreRoute

    data object Glossary : MoreRoute

    data object Settings : MoreRoute

    data object About : MoreRoute

    data object Licenses : MoreRoute
}

@Composable
fun MoreScreen(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<MoreRoute>(MoreRoute.Root) }

    when (route) {
        MoreRoute.Root -> MoreRoot(
            modifier = modifier,
            onOpenGlossary = { route = MoreRoute.Glossary },
            onOpenSettings = { route = MoreRoute.Settings },
            onOpenAbout = { route = MoreRoute.About },
        )

        MoreRoute.Glossary -> GlossaryScreen(modifier = modifier, onBack = { route = MoreRoute.Root })
        MoreRoute.Settings -> SettingsScreen(modifier = modifier, onBack = { route = MoreRoute.Root })
        MoreRoute.About -> AboutScreen(
            modifier = modifier,
            onBack = { route = MoreRoute.Root },
            onOpenLicenses = { route = MoreRoute.Licenses },
        )

        MoreRoute.Licenses -> LicensesScreen(modifier = modifier, onBack = { route = MoreRoute.About })
    }
}

@Composable
private fun MoreRoot(
    onOpenGlossary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.black).padding(NothingSpacing.md)) {
        // Doto wordmark, via NothingType.displayMd -> NothingFonts.display.
        Text("WIFILENS", style = NothingType.displayMd, color = colors.textDisplay)

        Column(modifier = Modifier.padding(top = NothingSpacing.xl2)) {
            MoreRow(label = "Glossary", onClick = onOpenGlossary)
            MoreRow(label = "Settings", onClick = onOpenSettings)
            MoreRow(label = "About", onClick = onOpenAbout)
        }
    }
}

@Composable
internal fun MoreRow(
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = WifiLensTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(vertical = NothingSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = NothingType.body, color = colors.textPrimary)
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Text(">", style = NothingType.body, color = colors.textDisabled)
            }
        }
        NothingDivider()
    }
}

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    val colors = WifiLensTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(NothingSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
    ) {
        Box(modifier = Modifier.clickable(onClick = onBack)) {
            Text("< BACK", style = NothingType.label, color = colors.textSecondary)
        }
        Text(title.uppercase(), style = NothingType.label, color = colors.textDisplay)
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun MoreRootPreview() {
    WifiLensTheme { MoreRoot(onOpenGlossary = {}, onOpenSettings = {}, onOpenAbout = {}) }
}
