package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Screen top app bar. [onBack] adds a back arrow (Up). [large] uses the Expressive large flexible bar for
 * top-level hub screens (More). Container is the screen surface, so the bar blends in until content scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WifiLensTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
) {
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            WifiLensIconButton(
                icon = WifiLensIcon.ArrowBack,
                contentDescription = stringResource(R.string.ds_action_back),
                onClick = onBack,
            )
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    if (large) {
        LargeFlexibleTopAppBar(
            title = { Text(title, modifier = Modifier.semantics { heading() }) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            colors = colors,
        )
    } else {
        TopAppBar(
            title = { Text(title, modifier = Modifier.semantics { heading() }) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            colors = colors,
        )
    }
}

/**
 * One-line or two-line list row (M3 [ListItem]) with an optional leading icon. A clickable row without a custom
 * [trailing] slot gets a chevron.
 */
@Composable
fun WifiLensListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: WifiLensIcon? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val supportingContent: (@Composable () -> Unit)? = supporting?.let { { Text(it) } }
    val leadingContent: (@Composable () -> Unit)? = icon?.let { { Icon(it.vector, contentDescription = null) } }
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = modifier,
            leadingContent = leadingContent,
            trailingContent = trailing ?: { Icon(WifiLensIcon.ChevronRight.vector, contentDescription = null) },
            supportingContent = supportingContent,
            colors = colors,
        ) { Text(headline) }
    } else {
        ListItem(
            modifier = modifier,
            leadingContent = leadingContent,
            trailingContent = trailing,
            supportingContent = supportingContent,
            colors = colors,
        ) { Text(headline) }
    }
}

/**
 * List row with a trailing switch where the **whole row** toggles (M3 guidance), exposed to TalkBack as a single
 * switch. The switch itself has no click handler, so there's one touch target and one announcement.
 */
@Composable
fun WifiLensSwitchListItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    // Not the checked/onCheckedChange ListItem overload: it announces the row as a checkbox, not a switch.
    ListItem(
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        enabled = enabled,
        trailingContent = { WifiLensSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = supporting?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Text(headline) }
}

/**
 * Single-line label that steps its font size down (to [minFontSize]) instead of wrapping or truncating, for
 * tight slots like navigation labels and segmented buttons at large font scales. Never grows past [style].
 */
@Composable
fun WifiLensFitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    minFontSize: TextUnit = 8.sp,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize),
    )
}
