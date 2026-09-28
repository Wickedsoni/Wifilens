package com.wickedcoder.wifilens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wickedcoder.wifilens.core.designsystem.NavItem
import com.wickedcoder.wifilens.core.designsystem.WifiLensFitText
import com.wickedcoder.wifilens.core.designsystem.WifiLensNavIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensTransitions
import com.wickedcoder.wifilens.feature.analyze.presentation.AnalyzeScreen
import com.wickedcoder.wifilens.feature.diagnose.presentation.DiagnoseScreen
import com.wickedcoder.wifilens.feature.map.presentation.MapScreen
import com.wickedcoder.wifilens.feature.more.presentation.MORE_GRAPH_ROUTE
import com.wickedcoder.wifilens.feature.more.presentation.moreGraph

private const val ROUTE_ANALYZE = "analyze"
private const val ROUTE_MAP = "map"
private const val ROUTE_DIAGNOSE = "diagnose"

private val topLevelDestinations = listOf(
    NavItem("Analyze", ROUTE_ANALYZE, WifiLensNavIcon.Analyze),
    NavItem("Map", ROUTE_MAP, WifiLensNavIcon.Map),
    NavItem("Diagnose", ROUTE_DIAGNOSE, WifiLensNavIcon.Diagnose),
    NavItem("More", MORE_GRAPH_ROUTE, WifiLensNavIcon.More),
)

/**
 * Owns the navigation graph and the adaptive navigation chrome (a bottom bar on phones, a rail on foldables and
 * tablets, picked by [NavigationSuiteScaffold] from the window size), so sibling feature modules never depend on
 * each other to switch tabs. Tabs sit on a real back stack: Back from any tab returns to Analyze (the start
 * destination) and only then leaves the app.
 */
@Composable
fun WifiLensApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        navigationSuiteItems = {
            topLevelDestinations.forEach { item ->
                // hierarchy: a More sub-screen (more/settings) still highlights the More tab.
                val selected = destination?.hierarchy?.any { it.route == item.route } ?: (item.route == ROUTE_ANALYZE)
                item(
                    selected = selected,
                    onClick = { navController.navigateToTab(item.route) },
                    icon = { Icon(imageVector = item.icon.vector, contentDescription = null) },
                    label = { WifiLensFitText(text = item.label) },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            // Tabs switch instantly; More's sub-screens override with a side-by-side slide in moreGraph.
            NavHost(
                navController = navController,
                startDestination = ROUTE_ANALYZE,
                enterTransition = { WifiLensTransitions.none },
                exitTransition = { WifiLensTransitions.noneExit },
                popEnterTransition = { WifiLensTransitions.none },
                popExitTransition = { WifiLensTransitions.noneExit },
            ) {
                composable(ROUTE_ANALYZE) { AnalyzeScreen(viewModel = hiltViewModel()) }
                composable(ROUTE_MAP) {
                    MapScreen(viewModel = hiltViewModel(), onRunDiagnosis = { navController.navigateToTab(ROUTE_DIAGNOSE) })
                }
                composable(ROUTE_DIAGNOSE) { DiagnoseScreen(viewModel = hiltViewModel()) }
                moreGraph(navController)
            }
        }
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
