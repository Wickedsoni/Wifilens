package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Space Mono, ALL CAPS label — "instrument panel" style used for every field label in the system. */
@Composable
fun NothingLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WifiLensTheme.colors.textSecondary,
) {
    Text(
        text = text.uppercase(),
        style = NothingType.label,
        color = color,
        modifier = modifier,
    )
}

/** components.md Section 8 — pill segmented control, active segment inverted. Max 2-4 segments. */
@Composable
fun NothingSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No forced fillMaxWidth here — a component meant for reuse inside constrained Rows (like
    // MapScreen's TopBar) must not presume it owns the whole row; on-device testing showed it
    // squeezing sibling content into near-zero width. Callers that want it full-width (Analyze,
    // Diagnose) add .fillMaxWidth() themselves.
    val colors = WifiLensTheme.colors
    Row(
        modifier = modifier
            .height(40.dp)
            .border(1.dp, colors.borderVisible, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    // Row's weight only distributes width. Without fillMaxHeight each segment
                    // wraps its 13sp text line and sits at the Row's top edge, so the inverted
                    // "selected" fill is a thin strip instead of filling the 40dp control.
                    .fillMaxHeight()
                    .background(if (selected) colors.textDisplay else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.uppercase(),
                    style = NothingType.label,
                    color = if (selected) colors.black else colors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** components.md Section 7 — outline pill tag/chip. Active state = display border + text. */
@Composable
fun NothingChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    // The touch target is 48dp tall (accessibility minimum) while the visible pill stays compact.
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(
                    width = 1.dp,
                    color = if (selected) colors.textDisplay else colors.borderVisible,
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = NothingSpacing.md, vertical = NothingSpacing.xs),
        ) {
            Text(
                text = text.uppercase(),
                style = NothingType.caption,
                color = if (selected) colors.textDisplay else colors.textSecondary,
            )
        }
    }
}

data class NavItem(val label: String, val route: String, val icon: NothingNavIcon)

/**
 * components.md Section 6 — bottom navigation. 72dp tall; the active tab is a filled capsule behind
 * an inverted icon, with its label in the display colour; inactive tabs are disabled-grey.
 */
@Composable
fun NothingBottomNavBar(
    items: List<NavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.black) // painted first so it also fills behind the gesture bar
            .border(width = 1.dp, color = colors.border)
            .navigationBarsPadding()
            .height(72.dp)
            .padding(top = 8.dp),
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 64.dp)
                        .then(
                            if (selected) {
                                Modifier.background(colors.textDisplay, RoundedCornerShape(999.dp))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    NothingNavGlyph(item.icon, tint = if (selected) colors.black else colors.textDisabled)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.label.uppercase(),
                    style = NothingType.label.copy(fontSize = 10.sp),
                    color = if (selected) colors.textDisplay else colors.textDisabled,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Surface card: --surface fill, 1dp --border outline, 12dp radius. Content is a padded Column. */
@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WifiLensTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .padding(NothingSpacing.md),
        content = content,
    )
}

/** components.md Section 4 — data row, label left / value right, divider below. */
@Composable
fun NothingDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WifiLensTheme.colors.border),
    )
}

/** components.md Section 15 — centered empty state, no mascots, one sentence of copy. */
@Composable
fun NothingEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = WifiLensTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NothingSpacing.xl4, horizontal = NothingSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NothingSpacing.sm),
    ) {
        Text(
            text = title.uppercase(),
            style = NothingType.subheading,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = NothingType.bodySmall,
            color = colors.textDisabled,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = NothingSpacing.md)) { action() }
        }
    }
}

/** components.md Section 2 — pill primary button, inverted fill. Min height 44dp. */
@Composable
fun NothingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.textDisplay)
            .clickable(onClick = onClick)
            .padding(horizontal = NothingSpacing.lg, vertical = NothingSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = NothingType.caption.copy(letterSpacing = 0.06.em, fontWeight = FontWeight.Normal),
            color = colors.black,
        )
    }
}

/** components.md Section 2 — ghost/secondary text button, no fill. */
@Composable
fun NothingGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = WifiLensTheme.colors.textSecondary,
) {
    Text(
        text = text.uppercase(),
        style = NothingType.caption,
        color = color,
        modifier = modifier
            .heightIn(min = 48.dp) // minimum touch target
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

/** Small round status dot, used for connection/signal state indicators. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** components.md Section 10 — pill track, circle thumb. Min touch target 44dp. */
@Composable
fun NothingToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = WifiLensTheme.colors
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 44.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) colors.textDisplay else colors.borderVisible),
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(16.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(if (checked) colors.black else colors.textDisabled),
            )
        }
    }
}

/**
 * components.md Section 14 — bottom sheet: --surface bg, 2px handle bar centered, 16px top
 * radius, drag-to-dismiss (native to Material3's ModalBottomSheet). Used for creation/entry flows
 * (create plan, new room) — centered Modal-style dialogs are for confirmations, a different
 * pattern per the same section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WifiLensTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = NothingSpacing.sm)
                    .size(width = 32.dp, height = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.borderVisible),
            )
        },
        content = content,
    )
}
