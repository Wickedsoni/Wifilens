package com.wickedcoder.wifilens.feature.analyze.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wickedcoder.wifilens.core.designsystem.SignalStatus
import com.wickedcoder.wifilens.core.designsystem.WifiLensCard
import com.wickedcoder.wifilens.core.designsystem.WifiLensChip
import com.wickedcoder.wifilens.core.designsystem.WifiLensDivider
import com.wickedcoder.wifilens.core.designsystem.WifiLensEmptyState
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensIconButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensLabel
import com.wickedcoder.wifilens.core.designsystem.WifiLensLoadingIndicator
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSegmentedControl
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.forSignalStatus
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.core.designsystem.warning
import com.wickedcoder.wifilens.feature.analyze.domain.BandFilter
import com.wickedcoder.wifilens.feature.analyze.domain.ChannelAdvice
import com.wickedcoder.wifilens.feature.analyze.domain.ConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.ScannedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.SpectrumBar
import com.wickedcoder.wifilens.feature.analyze.domain.SpectrumStats

@Composable
internal fun NetworksTab(
    state: NetworksTabState,
    onBandFilterSelected: (BandFilter) -> Unit,
    onSortSelected: (NetworkSort) -> Unit,
    onRefreshScan: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    // Connection header, scan status and filters. In the list case they scroll with it, so landscape
    // (where they'd otherwise eat most of the height) still leaves the networks reachable.
    val header: @Composable () -> Unit = {
        Column {
            ConnectionHeader(state.connection)

            ScanStatusLine(state.scanStatus, onRefreshScan)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
                    BandFilter.entries.forEach { filter ->
                        WifiLensChip(
                            text = filter.label,
                            selected = state.bandFilter == filter,
                            onClick = { onBandFilterSelected(filter) },
                        )
                    }
                }
                Text(
                    text = "SORT: ${state.sort.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
    val showsList = state.scanStatus !is ScanStatus.NotScanning &&
        state.scanStatus !is ScanStatus.WifiOff &&
        state.scanStatus !is ScanStatus.LocationOff &&
        state.visibleNetworks.isNotEmpty()

    if (showsList) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = WifiLensSpacing.sm),
        ) {
            item { header() }
            items(state.visibleNetworks, key = { it.id }) { network ->
                // Rows glide to their new position when a scan re-sorts the list; no fade in/out (user preference).
                Column(
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        .padding(horizontal = WifiLensSpacing.md),
                ) {
                    NetworkRow(network)
                    WifiLensDivider()
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        header()

        when {
            state.scanStatus is ScanStatus.NotScanning -> {
                WifiLensEmptyState(
                    title = "Scanning is off",
                    description = "Grant location access to see live networks and signal.",
                    action = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(WifiLensSpacing.md),
                        ) {
                            WifiLensPrimaryButton(text = "Grant access", onClick = {})
                            WifiLensTextButton(text = "Open map instead >", onClick = {})
                        }
                    },
                )
            }

            state.scanStatus is ScanStatus.WifiOff -> {
                WifiLensEmptyState(
                    title = "Wi-Fi is off",
                    description = "Turn the Wi-Fi radio on to see nearby networks. Scan results never leave the phone.",
                    action = {
                        WifiLensPrimaryButton(text = "Open Wi-Fi settings", onClick = onOpenWifiSettings)
                    },
                )
            }

            state.scanStatus is ScanStatus.LocationOff -> {
                WifiLensEmptyState(
                    title = "Location is off",
                    description = "Android hides nearby networks while location services are off. " +
                        "Scan results never leave the phone.",
                    action = {
                        WifiLensPrimaryButton(text = "Open location settings", onClick = onOpenLocationSettings)
                    },
                )
            }

            state.visibleNetworks.isEmpty() -> {
                WifiLensEmptyState(
                    title = "No networks found",
                    description = "The radio is on but nothing answered this scan.",
                )
            }

            else -> {
                Unit
            } // the list case returned above
        }
    }
}

@Composable
private fun ConnectionHeader(connection: ConnectionStatus) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth().padding(WifiLensSpacing.md)) {
        when (connection) {
            ConnectionStatus.Loading -> {
                WifiLensLabel("Connecting")
            }

            ConnectionStatus.Disconnected -> {
                WifiLensLabel("Not connected")
            }

            is ConnectionStatus.Connected -> {
                val network = connection.network
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WifiLensLabel("Connected")
                    Spacer(Modifier.width(WifiLensSpacing.sm))
                    Text(text = "[MEASURED]", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = WifiLensSpacing.xs),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${network.rssiDbm}",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.forSignalStatus(network.rssiDbm.toSignalStatus()),
                    )
                    Text(
                        text = " DBM",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = WifiLensSpacing.xs),
                    )
                }
                Text(
                    text = "CH ${network.channel} · ${network.band} GHZ",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = WifiLensSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ScanStatusLine(status: ScanStatus, onRefreshScan: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text = when (status) {
        ScanStatus.Scanning -> "[SCANNING]"
        is ScanStatus.Throttled -> "[THROTTLED · NEXT SCAN IN ${status.nextScanEtaSeconds}S]"
        is ScanStatus.Idle -> when (val ago = status.lastScanAgoSeconds) {
            null -> "NO SCAN YET"
            0 -> "UPDATED JUST NOW"
            else -> "UPDATED ${ago}S AGO"
        }
        ScanStatus.NotScanning -> "[NOT SCANNING]"
        ScanStatus.LocationOff -> "[LOCATION OFF]"
        ScanStatus.WifiOff -> "[WI-FI OFF]"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = WifiLensSpacing.md, end = WifiLensSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed slot, shown instantly: no fade-in and no sideways jump of the status text when a scan starts,
        // which happens every time Analyze is shown again (e.g. back from More, B-35 follow-up).
        Box(modifier = Modifier.padding(end = WifiLensSpacing.sm).size(24.dp)) {
            if (status is ScanStatus.Scanning) WifiLensLoadingIndicator(modifier = Modifier.fillMaxSize())
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        WifiLensIconButton(
            icon = WifiLensIcon.Refresh,
            contentDescription = "Scan again",
            onClick = onRefreshScan,
            enabled = status.canRefresh,
        )
    }
}

@Composable
private fun NetworkRow(network: ScannedNetwork) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WifiLensSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = network.ssid, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(
                text = "${network.bssidMasked} · ${network.security}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${network.rssiDbm}",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.forSignalStatus(network.rssiDbm.toSignalStatus()),
            )
            Text(
                text = "CH ${network.channel} · ${network.band}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun Int.toSignalStatus(): SignalStatus = when {
    this >= -60 -> SignalStatus.Good
    this >= -75 -> SignalStatus.Moderate
    else -> SignalStatus.Poor
}

// ---------------------------------------------------------------------------------------------
// Spectrum tab (mockup 1d)
// ---------------------------------------------------------------------------------------------
