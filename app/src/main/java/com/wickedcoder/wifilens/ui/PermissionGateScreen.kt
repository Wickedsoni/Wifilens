package com.wickedcoder.wifilens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wickedcoder.wifilens.core.designsystem.NothingDivider
import com.wickedcoder.wifilens.core.designsystem.NothingGhostButton
import com.wickedcoder.wifilens.core.designsystem.NothingLabel
import com.wickedcoder.wifilens.core.designsystem.NothingPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.NothingSpacing
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme

/** Mirrors mockup 1b — S1 · PERMISSION GATE, three states. */
sealed interface PermissionGateState {
    data object AllMissing : PermissionGateState
    data object PermanentlyDenied : PermissionGateState
    data object WifiOff : PermissionGateState
}

private data class StatusRow(val label: String, val value: String, val ok: Boolean)

@Composable
fun PermissionGateScreen(
    state: PermissionGateState,
    onGrantAccess: () -> Unit,
    onContinueWithoutScanning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WifiLensTheme.colors

    val (headline, body, rows, actionLabel) = when (state) {
        PermissionGateState.AllMissing -> Quad(
            "Scanning needs location access.",
            "Android requires location permission to read nearby networks. Nothing leaves the phone.",
            listOf(
                StatusRow("Location permission", "REQUIRED", ok = false),
                StatusRow("Location services", "OFF", ok = false),
                StatusRow("Wi-Fi", "OFF", ok = false),
            ),
            "GRANT ACCESS",
        )

        PermissionGateState.PermanentlyDenied -> Quad(
            "Scanning needs location access.",
            "Permission was denied permanently. Grant it in Android settings — nothing leaves the phone.",
            listOf(
                StatusRow("Location permission", "REQUIRED", ok = false),
                StatusRow("Location services", "ON", ok = true),
                StatusRow("Wi-Fi", "ON", ok = true),
            ),
            "OPEN SETTINGS",
        )

        PermissionGateState.WifiOff -> Quad(
            "Wi-Fi is switched off.",
            "Turn the radio on to scan. Permission is already granted; nothing leaves the phone.",
            listOf(
                StatusRow("Location permission", "GRANTED", ok = true),
                StatusRow("Location services", "ON", ok = true),
                StatusRow("Wi-Fi", "OFF", ok = false),
            ),
            "TURN ON WI-FI",
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(PaddingValues(horizontal = NothingSpacing.lg, vertical = NothingSpacing.xl3)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = headline,
                style = NothingType.heading,
                color = colors.textDisplay,
            )
            Text(
                text = body,
                style = NothingType.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = NothingSpacing.sm),
            )

            Column(
                modifier = Modifier.padding(top = NothingSpacing.xl2),
                verticalArrangement = Arrangement.spacedBy(NothingSpacing.md),
            ) {
                rows.forEach { row ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NothingLabel(row.label, color = colors.textSecondary)
                            Text(
                                text = row.value,
                                style = NothingType.caption,
                                color = if (row.ok) colors.success else colors.accent,
                            )
                        }
                        NothingDivider(modifier = Modifier.padding(top = NothingSpacing.sm))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(NothingSpacing.lg)) {
            NothingPrimaryButton(
                text = actionLabel,
                onClick = onGrantAccess,
                modifier = Modifier.fillMaxWidth(),
            )
            NothingGhostButton(
                text = "Continue without scanning",
                onClick = onContinueWithoutScanning,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "NO INTERNET PERMISSION · NO ACCOUNT · NO ADS",
                style = NothingType.caption,
                color = colors.textDisabled,
            )
        }
    }
}

private data class Quad(
    val headline: String,
    val body: String,
    val rows: List<StatusRow>,
    val actionLabel: String,
)

@Preview(showBackground = true)
@Composable
private fun PermissionGateAllMissingPreview() {
    WifiLensTheme {
        PermissionGateScreen(PermissionGateState.AllMissing, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionGateDeniedPreview() {
    WifiLensTheme {
        PermissionGateScreen(PermissionGateState.PermanentlyDenied, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionGateWifiOffPreview() {
    WifiLensTheme {
        PermissionGateScreen(PermissionGateState.WifiOff, {}, {})
    }
}
