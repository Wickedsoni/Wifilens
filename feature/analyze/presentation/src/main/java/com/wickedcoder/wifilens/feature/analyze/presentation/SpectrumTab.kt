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
internal fun SpectrumTab(
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
