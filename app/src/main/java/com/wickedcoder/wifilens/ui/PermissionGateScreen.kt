package com.wickedcoder.wifilens.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.wickedcoder.wifilens.GateReason
import com.wickedcoder.wifilens.GateUiState
import com.wickedcoder.wifilens.R
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensLogo
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.permissions.ScanPermission

private data class StatusRow(
    @StringRes val label: Int,
    @StringRes val value: Int,
    val ok: Boolean,
)

private data class GateCopy(
    @StringRes val headline: Int,
    @StringRes val body: Int,
    @StringRes val action: Int,
)

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
                label = R.string.app_gate_row_location_permission,
                value = when (state.permission) {
                    ScanPermission.Granted -> R.string.app_gate_status_allowed
                    ScanPermission.ApproximateOnly -> R.string.app_gate_status_approximate_only
                    ScanPermission.NotGranted -> R.string.app_gate_status_not_allowed
                },
                ok = state.permission == ScanPermission.Granted,
            ),
        )
        // Scanning also needs the device-wide Location toggle on.
        add(StatusRow(R.string.app_gate_row_location_services, onOff(state.locationServicesOn), ok = state.locationServicesOn))
        add(StatusRow(R.string.app_gate_row_wifi, onOff(state.wifiOn), ok = state.wifiOn))
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
                text = stringResource(copy.headline),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(copy.body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(row.label), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(
                        stringResource(row.value),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (row.ok) colors.success else colors.danger,
                    )
                }
                WifiLensDivider()
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            WifiLensPrimaryButton(text = stringResource(copy.action), onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth())
            WifiLensTextButton(
                text = stringResource(R.string.app_gate_continue_without_scanning),
                onClick = onContinueWithoutScanning,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.app_gate_privacy_footer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun gateCopy(reason: GateReason): GateCopy = when (reason) {
    GateReason.NeedsPermission -> GateCopy(
        R.string.app_gate_needs_permission_title,
        R.string.app_gate_needs_permission_body,
        R.string.app_gate_needs_permission_action,
    )
    GateReason.ApproximateOnly -> GateCopy(
        R.string.app_gate_approximate_title,
        R.string.app_gate_approximate_body,
        R.string.app_gate_approximate_action,
    )
    GateReason.PermanentlyDenied -> GateCopy(
        R.string.app_gate_denied_title,
        R.string.app_gate_denied_body,
        R.string.app_gate_denied_action,
    )
    GateReason.WifiOff -> GateCopy(
        R.string.app_gate_wifi_off_title,
        R.string.app_gate_wifi_off_body,
        R.string.app_gate_wifi_off_action,
    )
}

@StringRes
private fun onOff(on: Boolean): Int = if (on) R.string.app_gate_status_on else R.string.app_gate_status_off

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
