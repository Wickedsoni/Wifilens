package com.wickedcoder.wifilens.feature.analyze.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
fun AnalyzeScreen(
    viewModel: AnalyzeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Every time the app returns to the foreground, not just at ViewModel creation. Lives here
    // rather than in the ViewModel because the ViewModel has no Lifecycle to repeat on.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.onResumed() }
    }
    AnalyzeContent(
        state = state,
        onTabSelected = viewModel::onTabSelected,
        onBandFilterSelected = viewModel::onBandFilterSelected,
        onSortSelected = viewModel::onSortSelected,
        onSpectrumBandSelected = viewModel::onSpectrumBandSelected,
        onRefreshScan = viewModel::onRefreshScan,
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onOpenWifiSettings = {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        modifier = modifier,
    )
}

@Composable
fun AnalyzeContent(
    state: AnalyzeState,
    onTabSelected: (AnalyzeTab) -> Unit,
    onBandFilterSelected: (BandFilter) -> Unit,
    onSortSelected: (NetworkSort) -> Unit,
    onSpectrumBandSelected: (BandFilter) -> Unit,
    onRefreshScan: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        WifiLensSegmentedControl(
            items = AnalyzeTab.entries.map { it.label },
            selectedIndex = state.selectedTab.ordinal,
            onSelect = { onTabSelected(AnalyzeTab.entries[it]) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm),
        )

        Box(modifier = Modifier.weight(1f)) {
            when (state.selectedTab) {
                AnalyzeTab.Networks -> NetworksTab(
                    state = state.networksTab,
                    onBandFilterSelected = onBandFilterSelected,
                    onSortSelected = onSortSelected,
                    onRefreshScan = onRefreshScan,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenWifiSettings = onOpenWifiSettings,
                )

                AnalyzeTab.Spectrum -> SpectrumTab(
                    state = state.spectrumTab,
                    onBandSelected = onSpectrumBandSelected,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Networks tab (mockup 1c)
// ---------------------------------------------------------------------------------------------

@Composable
private fun NetworksTab(
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
            items(state.visibleNetworks) { network ->
                Column(modifier = Modifier.padding(horizontal = WifiLensSpacing.md)) {
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
        AnimatedVisibility(visible = status is ScanStatus.Scanning) {
            WifiLensLoadingIndicator(modifier = Modifier.padding(end = WifiLensSpacing.sm).size(24.dp))
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

@Composable
private fun SpectrumTab(
    state: SpectrumTabState,
    onBandSelected: (BandFilter) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(WifiLensSpacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm)) {
            state.availableBands.forEach { band ->
                WifiLensChip(
                    text = "${band.label} GHZ",
                    selected = state.band == band,
                    onClick = { onBandSelected(band) },
                )
            }
        }

        Spacer(Modifier.height(WifiLensSpacing.lg))

        if (state.bars.isEmpty()) {
            WifiLensEmptyState(
                title = "No spectrum data",
                description = "Scan the area to see channel congestion.",
            )
        } else {
            SpectrumChart(state.bars)
        }

        Spacer(Modifier.height(WifiLensSpacing.xl))

        WifiLensLabel("Congestion score · 0 clear — 100 crowded")
        WifiLensDivider(modifier = Modifier.padding(vertical = WifiLensSpacing.sm))

        StatRow("Co-channel networks", state.stats.coChannelCount.toString())
        StatRow("Overlapping networks", state.stats.overlappingCount.toString())
        StatRow(
            "Strongest interferer",
            state.stats.strongestInterfererDbm?.let { "$it DBM" } ?: "—",
        )

        state.advice?.let { advice ->
            Spacer(Modifier.height(WifiLensSpacing.lg))
            BestChannelCard(advice)
        }
    }
}

@Composable
private fun BestChannelCard(advice: ChannelAdvice) {
    val colors = MaterialTheme.colorScheme

    WifiLensCard {
        WifiLensLabel("Best channel")
        Spacer(Modifier.height(WifiLensSpacing.sm))

        when (advice) {
            is ChannelAdvice.Switch -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CH ${advice.channel}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
                            color = colors.onSurface,
                        )
                        Text(
                            text = "${advice.band} GHZ · ${advice.congestionScore} CONGESTION SCORE",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        if (advice.isDfs) {
                            Text(
                                text = "(DFS — MAY PAUSE)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warning,
                            )
                        }
                    }
                    Text(
                        text = "LEAST BUSY",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.success,
                        modifier = Modifier
                            .border(1.dp, colors.success, RoundedCornerShape(999.dp))
                            .padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.xs),
                    )
                }
            }

            ChannelAdvice.AlreadyOptimal -> {
                Text(
                    text = "YOUR CHANNEL IS ALREADY OPTIMAL",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        WifiLensDivider(modifier = Modifier.padding(vertical = WifiLensSpacing.md))
        Text(
            text = "This is the least crowded channel detected in this scan. " +
                "Apply it in your router's admin page — WiFiLens cannot change router settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpectrumChart(bars: List<SpectrumBar>) {
    val colors = MaterialTheme.colorScheme
    val maxScore = 100
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${bar.congestionScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .padding(top = WifiLensSpacing.xs)
                        .fillMaxWidth()
                        .height((bar.congestionScore.coerceIn(4, maxScore) * 1.2).dp)
                        .background(barColor(bar.congestionScore, colors)),
                )
                Text(
                    text = "${bar.channel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = WifiLensSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun barColor(congestion: Int, colors: ColorScheme): Color = when {
    congestion >= 75 -> colors.danger
    congestion >= 45 -> colors.warning
    else -> colors.onSurface
}

@Composable
private fun StatRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WifiLensSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        WifiLensLabel(label)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
    WifiLensDivider()
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val sampleNetworks = listOf(
    ScannedNetwork("ORBIT-7", "A4:3E··1D", "WPA3", -61, 6, "2.4"),
    ScannedNetwork("NETGEAR-2447", "C0:56··9A", "WPA2", -58, 11, "2.4"),
    ScannedNetwork("TP-LINK_9F20", "7C:8B··20", "WPA2", -63, 1, "2.4"),
    ScannedNetwork("[HIDDEN]", "1E:44··07", "WPA2", -66, 44, "5"),
    ScannedNetwork("SKYNET-5", "B8:27··F1", "WPA3", -68, 149, "5"),
    ScannedNetwork("CAFE_FREE", "3A:11··88", "SECURITY NONE", -71, 6, "2.4"),
)

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun NetworksScanningPreview() {
    WifiLensTheme {
        AnalyzeContent(
            state = AnalyzeState(
                networksTab = NetworksTabState(
                    connection = ConnectionStatus.Connected(
                        ConnectedNetwork("ORBIT-7", -54, 36, "5", 80, "WI-FI 6"),
                    ),
                    scanStatus = ScanStatus.Scanning,
                    networks = sampleNetworks,
                ),
            ),
            onTabSelected = {},
            onBandFilterSelected = {},
            onSortSelected = {},
            onSpectrumBandSelected = {},
            onRefreshScan = {},
            onOpenLocationSettings = {},
            onOpenWifiSettings = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun NetworksEmptyPreview() {
    WifiLensTheme {
        AnalyzeContent(
            state = AnalyzeState(
                networksTab = NetworksTabState(
                    connection = ConnectionStatus.Disconnected,
                    scanStatus = ScanStatus.Idle(12),
                ),
            ),
            onTabSelected = {},
            onBandFilterSelected = {},
            onSortSelected = {},
            onSpectrumBandSelected = {},
            onRefreshScan = {},
            onOpenLocationSettings = {},
            onOpenWifiSettings = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun NetworksNotScanningPreview() {
    WifiLensTheme {
        AnalyzeContent(
            state = AnalyzeState(
                networksTab = NetworksTabState(
                    connection = ConnectionStatus.Disconnected,
                    scanStatus = ScanStatus.NotScanning,
                ),
            ),
            onTabSelected = {},
            onBandFilterSelected = {},
            onSortSelected = {},
            onSpectrumBandSelected = {},
            onRefreshScan = {},
            onOpenLocationSettings = {},
            onOpenWifiSettings = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 917, widthDp = 412)
@Composable
private fun SpectrumPreview() {
    WifiLensTheme {
        AnalyzeContent(
            state = AnalyzeState(
                selectedTab = AnalyzeTab.Spectrum,
                spectrumTab = SpectrumTabState(
                    band = BandFilter.Band24,
                    bars = listOf(
                        SpectrumBar(1, 62, listOf("TP-LINK_9F20"), -63),
                        SpectrumBar(3, 71, listOf("BT-HUB-882"), -77),
                        SpectrumBar(6, 88, listOf("ORBIT-7", "CAFE_FREE"), -61),
                        SpectrumBar(9, 44, listOf("PLUSNET-KX9"), -86),
                        SpectrumBar(11, 29, listOf("NETGEAR-2447"), -58),
                        SpectrumBar(13, 53, listOf("IOT-BRIDGE"), -88),
                    ),
                    stats = SpectrumStats(coChannelCount = 3, overlappingCount = 7, strongestInterfererDbm = -48),
                ),
            ),
            onTabSelected = {},
            onBandFilterSelected = {},
            onSortSelected = {},
            onSpectrumBandSelected = {},
            onRefreshScan = {},
            onOpenLocationSettings = {},
            onOpenWifiSettings = {},
        )
    }
}
