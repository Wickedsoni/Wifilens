package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensListItem
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.WifiLensTopAppBar

/** The More tab's hub: a large Expressive title and one row per sub-screen. */
@Composable
internal fun MoreRoot(
    onOpenGlossary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WifiLensTopAppBar(title = "WifiLens", large = true)
        MoreRow(label = "Glossary", icon = WifiLensIcon.Help, onClick = onOpenGlossary)
        MoreRow(label = "Settings", icon = WifiLensIcon.Settings, onClick = onOpenSettings)
        MoreRow(label = "About", icon = WifiLensIcon.Info, onClick = onOpenAbout)
    }
}

/** A More/Settings/About row: M3 list item, chevron when clickable unless a custom [trailing] is given. */
@Composable
internal fun MoreRow(
    label: String,
    icon: WifiLensIcon? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    WifiLensListItem(headline = label, icon = icon, onClick = onClick, trailing = trailing)
}

/** Top app bar with Up navigation for the More sub-screens. */
@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    WifiLensTopAppBar(title = title, onBack = onBack)
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun MoreRootPreview() {
    WifiLensTheme { MoreRoot(onOpenGlossary = {}, onOpenSettings = {}, onOpenAbout = {}) }
}
