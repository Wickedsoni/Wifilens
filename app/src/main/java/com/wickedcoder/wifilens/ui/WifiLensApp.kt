package com.wickedcoder.wifilens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wickedcoder.wifilens.core.designsystem.NavItem
import com.wickedcoder.wifilens.core.designsystem.NothingBottomNavBar
import com.wickedcoder.wifilens.core.designsystem.NothingNavIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.feature.analyze.presentation.AnalyzeScreen
import com.wickedcoder.wifilens.feature.diagnose.presentation.DiagnoseScreen
import com.wickedcoder.wifilens.feature.map.presentation.MapScreen
import com.wickedcoder.wifilens.feature.more.presentation.MoreScreen
import org.koin.androidx.compose.koinViewModel

private const val ROUTE_ANALYZE = "analyze"
private const val ROUTE_MAP = "map"
private const val ROUTE_DIAGNOSE = "diagnose"
private const val ROUTE_MORE = "more"

private val bottomNavItems = listOf(
    NavItem("Analyze", ROUTE_ANALYZE, NothingNavIcon.Analyze),
    NavItem("Map", ROUTE_MAP, NothingNavIcon.Map),
    NavItem("Diagnose", ROUTE_DIAGNOSE, NothingNavIcon.Diagnose),
    NavItem("More", ROUTE_MORE, NothingNavIcon.More),
)

/**
 * Owns the navigation graph and the single shared bottom nav bar, so sibling feature modules never
 * need to depend on each other to switch tabs. Tabs sit on a real back stack: Back from any tab
 * returns to Analyze (the start destination) and only then leaves the app.
 */
@Composable
fun WifiLensApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route ?: ROUTE_ANALYZE
    val colors = WifiLensTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = ROUTE_ANALYZE) {
                composable(ROUTE_ANALYZE) { AnalyzeScreen(viewModel = koinViewModel()) }
                composable(ROUTE_MAP) {
                    MapScreen(viewModel = koinViewModel(), onRunDiagnosis = { navController.navigateToTab(ROUTE_DIAGNOSE) })
                }
                composable(ROUTE_DIAGNOSE) { DiagnoseScreen(viewModel = koinViewModel()) }
                composable(ROUTE_MORE) { MoreScreen() }
            }
        }
        NothingBottomNavBar(
            items = bottomNavItems,
            selectedRoute = selectedRoute,
            onSelect = navController::navigateToTab,
        )
    }
}

/** Switches tab without stacking duplicates, keeping each tab's saved state for when it is revisited. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
