package com.wickedcoder.wifilens.core.model

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { System, Dark, Light }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** Wallpaper-derived Material colours (Android 12+). Off by default; signal/heat-map colours never follow it. */
    val dynamicColor: Boolean = false,
    /** Master switch; the three below only apply while this is on. */
    val hapticsEnabled: Boolean = true,
    /** A tick for each new tile painted during a drag. */
    val hapticPaint: Boolean = true,
    /** Pin placed, plan created. */
    val hapticConfirm: Boolean = true,
    /** Invalid placement (e.g. router on a wall). */
    val hapticError: Boolean = true,
    val autoScanEnabled: Boolean = true,
    /** Fed into every `predictRssi` (core:rf) call — see DiagnoseViewModel. */
    val pathLossExponent: Float = 3.0f,
    val referenceRssiAt1m: Float = -40f,
)

/** App-wide preferences: theme, haptics, auto-scan, and the RF prediction model's two tunable
 * constants (path-loss exponent, reference RSSI at 1m). Backed by DataStore, not Room — this is
 * unstructured key-value settings, not relational data with a schema worth migrating. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setHapticPaint(enabled: Boolean)

    suspend fun setHapticConfirm(enabled: Boolean)

    suspend fun setHapticError(enabled: Boolean)

    suspend fun setAutoScanEnabled(enabled: Boolean)

    suspend fun setPathLossExponent(value: Float)

    suspend fun setReferenceRssiAt1m(value: Float)

    /** Restores path-loss exponent and reference RSSI to [AppSettings] defaults in one write. */
    suspend fun resetPredictionModel()
}
