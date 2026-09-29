package com.wickedcoder.wifilens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.wickedcoder.wifilens.GateReason
import com.wickedcoder.wifilens.GateUiState
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLogo
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.permissions.ScanPermission

private data class StatusRow(val label: String, val value: String, val ok: Boolean)

private data class GateCopy(val headline: String, val body: String, val action: String)

/**
 * The in-context explanation shown before any system permission dialog, and the recovery screen afterwards
 * (denied for good, approximate location only, Wi-Fi off). Status rows show the device's real state.
 */
@Composable
fun PermissionGateScreen(
    state: GateUiState.Blocked,
    onPrimaryAction: () -> Unit,
    onContinueWithoutScanning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val copy = gateCopy(state.reason)
    val rows = buildList {
        add(
            StatusRow(
                label = "Location permission",
                value = when (state.permission) {
                    ScanPermission.Granted -> "Allowed"
                    ScanPermission.ApproximateOnly -> "Approximate only"
                    ScanPermission.NotGranted -> "Not allowed"
                },
                ok = state.permission == ScanPermission.Granted,
            ),
        )
        // Scanning also needs the device-wide Location toggle on.
        add(StatusRow("Location services", if (state.locationServicesOn) "On" else "Off", ok = state.locationServicesOn))
        add(StatusRow("Wi-Fi", if (state.wifiOn) "On" else "Off", ok = state.wifiOn))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WifiLensSpacing.lg, vertical = WifiLensSpacing.xl2),
        verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            WifiLensLogo(modifier = Modifier.padding(bottom = WifiLensSpacing.md))
            Text(
                text = copy.headline,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(
                        row.value,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (row.ok) colors.success else colors.danger,
                    )
                }
                WifiLensDivider()
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            WifiLensPrimaryButton(text = copy.action, onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth())
            WifiLensTextButton(
                text = "Continue without scanning",
                onClick = onContinueWithoutScanning,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Scan results stay on your phone. The internet is only used for the optional speed test. " +
                    "No account, no ads.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun gateCopy(reason: GateReason): GateCopy = when (reason) {
    GateReason.NeedsPermission -> GateCopy(
        headline = "Allow location to scan Wi-Fi",
        body = "Android only shows nearby Wi-Fi networks to apps with precise location permission. WifiLens uses " +
            "it to read signal strength and channels, and never records or shares your location.",
        action = "Allow",
    )

    GateReason.ApproximateOnly -> GateCopy(
        headline = "Precise location needed",
        body = "You allowed approximate location, but Android only shares Wi-Fi scan results with precise " +
            "location. WifiLens never records or shares it.",
        action = "Allow precise location",
    )

    GateReason.PermanentlyDenied -> GateCopy(
        headline = "Permission is turned off",
        body = "Android won't ask again. Open WifiLens settings, tap Permissions, then Location, and choose " +
            "\"Allow only while using the app\" with precise location on.",
        action = "Open settings",
    )

    GateReason.WifiOff -> GateCopy(
        headline = "Wi-Fi is off",
        body = "Turn on Wi-Fi to scan the networks around you. You don't need to connect to one.",
        action = "Turn on Wi-Fi",
    )
}

@Preview(showBackground = true)
@Composable
private fun PermissionGateNeedsPermissionPreview() {
    WifiLensTheme {
        PermissionGateScreen(
            state = GateUiState.Blocked(GateReason.NeedsPermission, ScanPermission.NotGranted, wifiOn = true, locationServicesOn = true),
            onPrimaryAction = {},
            onContinueWithoutScanning = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionGateDeniedPreview() {
    WifiLensTheme {
        PermissionGateScreen(
            state = GateUiState.Blocked(GateReason.PermanentlyDenied, ScanPermission.NotGranted, wifiOn = true, locationServicesOn = false),
            onPrimaryAction = {},
            onContinueWithoutScanning = {},
        )
    }
}
