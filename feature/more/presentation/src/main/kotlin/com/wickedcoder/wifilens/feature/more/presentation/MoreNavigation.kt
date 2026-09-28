package com.wickedcoder.wifilens.feature.more.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

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
        composable(MORE_ROOT_ROUTE) {
            MoreRoot(
                onOpenGlossary = { navController.navigate(GLOSSARY_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
            )
        }
        composable(GLOSSARY_ROUTE) { GlossaryScreen(onBack = navController::popBackStack) }
        composable(SETTINGS_ROUTE) { SettingsScreen(onBack = navController::popBackStack) }
        composable(ABOUT_ROUTE) {
            AboutScreen(onBack = navController::popBackStack, onOpenLicenses = { navController.navigate(LICENSES_ROUTE) })
        }
        composable(LICENSES_ROUTE) { LicensesScreen(onBack = navController::popBackStack) }
    }
}
