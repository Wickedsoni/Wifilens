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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    onRequestScanAccess: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    historyViewModel: AnalyzeHistoryViewModel = hiltViewModel(),
    insightsViewModel: InsightsViewModel = hiltViewModel(),
) {
    val insights by insightsViewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val networkHistory by historyViewModel.networkHistory.collectAsStateWithLifecycle()
    val busyHours by historyViewModel.busyHours.collectAsStateWithLifecycle()
    LaunchedEffect(state.spectrumTab.band) { historyViewModel.onSpectrumBand(state.spectrumTab.band.label) }
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
        onRequestScanAccess = onRequestScanAccess,
        onOpenMap = onOpenMap,
        onOpenWifiSettings = {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        modifier = modifier,
        onNetworkSelected = historyViewModel::showHistory,
        busyHours = busyHours,
        insights = insights,
        onRunHealthCheck = insightsViewModel::runHealthCheck,
    )
    networkHistory?.let { NetworkHistorySheet(it, onDismiss = historyViewModel::dismissHistory) }
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
    onRequestScanAccess: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onNetworkSelected: (ScannedNetwork) -> Unit = {},
    busyHours: List<Float?> = emptyList(),
    insights: InsightsState = InsightsState(),
    onRunHealthCheck: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        WifiLensSegmentedControl(
            items = AnalyzeTab.entries.map { stringResource(it.label) },
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
                    onRequestScanAccess = onRequestScanAccess,
                    onOpenMap = onOpenMap,
                    onNetworkSelected = onNetworkSelected,
                )

                AnalyzeTab.Spectrum -> SpectrumTab(
                    state = state.spectrumTab,
                    onBandSelected = onSpectrumBandSelected,
                    busyHours = busyHours,
                )

                AnalyzeTab.Health -> HealthTab(state = insights, onRunCheck = onRunHealthCheck)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Networks tab (mockup 1c)
// ---------------------------------------------------------------------------------------------
