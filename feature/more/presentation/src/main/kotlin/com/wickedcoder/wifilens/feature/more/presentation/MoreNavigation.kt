package com.wickedcoder.wifilens.feature.more.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.wickedcoder.wifilens.core.designsystem.WifiLensTransitions

/** Route of the More tab's nested graph; the app shell navigates here when the More tab is selected. */
const val MORE_GRAPH_ROUTE = "more"

private const val MORE_ROOT_ROUTE = "more/root"
private const val GLOSSARY_ROUTE = "more/glossary"
private const val SETTINGS_ROUTE = "more/settings"
private const val ABOUT_ROUTE = "more/about"
private const val LICENSES_ROUTE = "more/licenses"

/**
 * The More tab as a nested graph, so Glossary/Settings/About/Licenses sit on the real back stack: system and
 * predictive back work, the open sub-screen survives rotation and process death, and switching tabs restores it.
 */
fun NavGraphBuilder.moreGraph(navController: NavController) {
    navigation(startDestination = MORE_ROOT_ROUTE, route = MORE_GRAPH_ROUTE) {
        composable(
            route = MORE_ROOT_ROUTE,
            // Into a sub-screen: shared axis. Anything else (switching tabs): the NavHost's fade-through.
            exitTransition = {
                if (targetState.isMoreChild()) WifiLensTransitions.sharedAxisForwardExit else WifiLensTransitions.fadeThroughExit
            },
            popEnterTransition = {
                if (initialState.isMoreChild()) WifiLensTransitions.sharedAxisBackEnter else WifiLensTransitions.fadeThroughEnter
            },
        ) {
            MoreRoot(
                onOpenGlossary = { navController.navigate(GLOSSARY_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
            )
        }
        moreChild(GLOSSARY_ROUTE) { GlossaryScreen(onBack = navController::popBackStack) }
        moreChild(SETTINGS_ROUTE) { SettingsScreen(onBack = navController::popBackStack) }
        moreChild(ABOUT_ROUTE) {
            AboutScreen(onBack = navController::popBackStack, onOpenLicenses = { navController.navigate(LICENSES_ROUTE) })
        }
        moreChild(LICENSES_ROUTE) { LicensesScreen(onBack = navController::popBackStack) }
    }
}

private fun NavBackStackEntry.isMoreChild(): Boolean = destination.route in MORE_CHILD_ROUTES

private val MORE_CHILD_ROUTES = setOf(GLOSSARY_ROUTE, SETTINGS_ROUTE, ABOUT_ROUTE, LICENSES_ROUTE)

/** A More sub-screen: shared-axis X in both directions between siblings/parent, fade-through when leaving the tab. */
private fun NavGraphBuilder.moreChild(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(
        route = route,
        enterTransition = { WifiLensTransitions.sharedAxisForwardEnter },
        exitTransition = {
            if (targetState.isMoreChild()) WifiLensTransitions.sharedAxisForwardExit else WifiLensTransitions.fadeThroughExit
        },
        popEnterTransition = { WifiLensTransitions.sharedAxisBackEnter },
        popExitTransition = { WifiLensTransitions.sharedAxisBackExit },
    ) { entry -> content(entry) }
}
