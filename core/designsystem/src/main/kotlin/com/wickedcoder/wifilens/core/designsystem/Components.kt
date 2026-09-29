package com.wickedcoder.wifilens.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// App-wide Material 3 Expressive building blocks. They only add WifiLens defaults (spacing, shape, semantics) on top
// of stock M3 components and take every colour from MaterialTheme.colorScheme, so dynamic colour, dark mode and the
// contrast levels apply automatically. Expressive APIs are opted in here only, never in feature modules (ADR 0005).

/** Section/field label (M3 labelLarge, on-surface-variant). */
@Composable
fun WifiLensLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier)
}

/** Single-choice connected buttons (2-4 options) on [SingleChoiceSegmentedButtonRow]. */
@Composable
fun WifiLensSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No forced fillMaxWidth: callers inside constrained Rows (the Map top bar) keep control of the width.
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                label = { WifiLensFitText(text = item) },
            )
        }
    }
}

/** Filter chip (room pickers, band filters). */
@Composable
fun WifiLensChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(text = text) },
        leadingIcon = leadingIcon,
    )
}

/** Tab destination for the adaptive navigation scaffold in `:app`. */
data class NavItem(
    @StringRes val label: Int,
    val route: String,
    val icon: WifiLensNavIcon,
)

/** Filled card on surfaceContainerLow with large expressive corners; content is a padded Column. */
@Composable
fun WifiLensCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(WifiLensSpacing.md), content = content)
    }
}

@Composable
fun WifiLensDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Centered empty state: title, one sentence of copy, optional action. */
@Composable
fun WifiLensEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = WifiLensSpacing.xl3, horizontal = WifiLensSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = WifiLensSpacing.md)) { action() }
        }
    }
}

/** Filled primary action. */
@Composable
fun WifiLensPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(text) }
}

/** Low-emphasis text action (min 48 dp touch target). */
@Composable
fun WifiLensTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) { Text(text) }
}

/** Small round status dot, used for connection/signal state indicators. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** M3 switch with a check icon in the thumb when on. */
@Composable
fun WifiLensSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = WifiLensIcon.Check.vector,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

/** Modal bottom sheet for creation/entry flows (create plan, add room). Confirmations use dialogs instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiLensBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState, content = content)
}

/** Expressive morphing-shape loading indicator (scanning, computing). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WifiLensLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier)
}

/** Expressive wavy determinate progress (speed test). [progress] returns 0..1. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WifiLensWavyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(progress = progress, modifier = modifier.fillMaxWidth())
}

/**
 * Expressive floating toolbar of mutually exclusive tools (the Map editor), built from toggle buttons. It scrolls
 * horizontally when the labels don't fit (narrow phones, large font scale). Each button reports `selected` so
 * TalkBack and tests see which tool is active.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WifiLensToolSelector(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.xs2),
        ) {
            items.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelect(index) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = isSelected },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(text = label, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
