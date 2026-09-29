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
import com.wickedcoder.wifilens.core.model.WifiSecurity
import com.wickedcoder.wifilens.feature.analyze.domain.BandFilter
import com.wickedcoder.wifilens.feature.analyze.domain.ChannelAdvice
import com.wickedcoder.wifilens.feature.analyze.domain.ConnectedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.ScannedNetwork
import com.wickedcoder.wifilens.feature.analyze.domain.SpectrumBar
import com.wickedcoder.wifilens.feature.analyze.domain.SpectrumStats

private val sampleNetworks = listOf(
    ScannedNetwork("ORBIT-7", "A4:3E··1D", WifiSecurity.WPA3, -61, 6, "2.4"),
    ScannedNetwork("NETGEAR-2447", "C0:56··9A", WifiSecurity.WPA2, -58, 11, "2.4"),
    ScannedNetwork("TP-LINK_9F20", "7C:8B··20", WifiSecurity.WPA2, -63, 1, "2.4"),
    ScannedNetwork("[HIDDEN]", "1E:44··07", WifiSecurity.WPA2, -66, 44, "5"),
    ScannedNetwork("SKYNET-5", "B8:27··F1", WifiSecurity.WPA3, -68, 149, "5"),
    ScannedNetwork("CAFE_FREE", "3A:11··88", WifiSecurity.Open, -71, 6, "2.4"),
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
