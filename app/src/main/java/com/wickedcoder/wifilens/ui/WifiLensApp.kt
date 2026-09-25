package com.wickedcoder.wifilens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wickedcoder.wifilens.core.designsystem.NavItem
import com.wickedcoder.wifilens.core.designsystem.NothingBottomNavBar
import com.wickedcoder.wifilens.core.designsystem.NothingNavIcon
import com.wickedcoder.wifilens.core.designsystem.NothingType
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.feature.analyze.presentation.AnalyzeScreen
import com.wickedcoder.wifilens.feature.diagnose.presentation.DiagnoseScreen
import com.wickedcoder.wifilens.feature.map.presentation.MapScreen
import com.wickedcoder.wifilens.feature.more.presentation.MoreScreen
import org.koin.androidx.compose.koinViewModel

private val bottomNavItems = listOf(
    NavItem("Analyze", "analyze", NothingNavIcon.Analyze),
    NavItem("Map", "map", NothingNavIcon.Map),
    NavItem("Diagnose", "diagnose", NothingNavIcon.Diagnose),
    NavItem("More", "more", NothingNavIcon.More),
)

/**
 * Owns the single shared bottom nav bar so sibling feature modules (:feature:analyze:presentation,
 * :feature:map, ...) never need to depend on each other to switch tabs.
 */
@Composable
fun WifiLensApp(modifier: Modifier = Modifier) {
    var selectedRoute by rememberSaveable { mutableStateOf("analyze") }
    val colors = WifiLensTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedRoute) {
                "analyze" -> AnalyzeScreen(viewModel = koinViewModel())
                "map" -> MapScreen(viewModel = koinViewModel(), onRunDiagnosis = { selectedRoute = "diagnose" })
                "diagnose" -> DiagnoseScreen(viewModel = koinViewModel())
                "more" -> MoreScreen()
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("${selectedRoute.replaceFirstChar { it.uppercase() }} — coming soon", style = NothingType.body, color = colors.textSecondary)
                }
            }
        }
        NothingBottomNavBar(
            items = bottomNavItems,
            selectedRoute = selectedRoute,
            onSelect = { selectedRoute = it },
        )
    }
}
