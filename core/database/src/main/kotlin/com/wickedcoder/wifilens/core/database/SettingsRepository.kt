package com.wickedcoder.wifilens.core.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Dark, Light }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** Master switch; the three below only apply while this is on. */
    val hapticsEnabled: Boolean = true,
    /** A tick for each new tile painted during a drag. */
    val hapticPaint: Boolean = true,
    /** Pin placed, plan created. */
    val hapticConfirm: Boolean = true,
    /** Invalid placement (e.g. router on a wall). */
    val hapticError: Boolean = true,
    val autoScanEnabled: Boolean = true,
    /** Fed into every [com.wickedcoder.wifilens.core.rf.predictRssi] call — see DiagnoseViewModel. */
    val pathLossExponent: Float = 3.0f,
    val referenceRssiAt1m: Float = -40f,
)

private val Context.settingsDataStore by preferencesDataStore(name = "wifilens_settings")

private object Keys {
    val THEME = stringPreferencesKey("theme")
    val HAPTICS = booleanPreferencesKey("haptics_enabled")
    val HAPTIC_PAINT = booleanPreferencesKey("haptic_paint")
    val HAPTIC_CONFIRM = booleanPreferencesKey("haptic_confirm")
    val HAPTIC_ERROR = booleanPreferencesKey("haptic_error")
    val AUTO_SCAN = booleanPreferencesKey("auto_scan_enabled")
    val PATH_LOSS_EXPONENT = floatPreferencesKey("path_loss_exponent")
    val REFERENCE_RSSI = floatPreferencesKey("reference_rssi_at_1m")
}

/** App-wide preferences: theme, haptics, auto-scan, and the RF prediction model's two tunable
 * constants (path-loss exponent, reference RSSI at 1m). Backed by DataStore, not Room — this is
 * unstructured key-value settings, not relational data with a schema worth migrating. */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: ThemeMode)
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

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            hapticPaint = prefs[Keys.HAPTIC_PAINT] ?: true,
            hapticConfirm = prefs[Keys.HAPTIC_CONFIRM] ?: true,
            hapticError = prefs[Keys.HAPTIC_ERROR] ?: true,
            autoScanEnabled = prefs[Keys.AUTO_SCAN] ?: true,
            pathLossExponent = prefs[Keys.PATH_LOSS_EXPONENT] ?: 3.0f,
            referenceRssiAt1m = prefs[Keys.REFERENCE_RSSI] ?: -40f,
        )
    }

    override suspend fun setTheme(theme: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    override suspend fun setHapticPaint(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTIC_PAINT] = enabled }
    }

    override suspend fun setHapticConfirm(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTIC_CONFIRM] = enabled }
    }

    override suspend fun setHapticError(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTIC_ERROR] = enabled }
    }

    override suspend fun setAutoScanEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SCAN] = enabled }
    }

    override suspend fun setPathLossExponent(value: Float) {
        context.settingsDataStore.edit { it[Keys.PATH_LOSS_EXPONENT] = value.coerceIn(2.0f, 4.5f) }
    }

    override suspend fun setReferenceRssiAt1m(value: Float) {
        context.settingsDataStore.edit { it[Keys.REFERENCE_RSSI] = value.coerceIn(-55f, -30f) }
    }

    override suspend fun resetPredictionModel() {
        val defaults = AppSettings()
        context.settingsDataStore.edit {
            it[Keys.PATH_LOSS_EXPONENT] = defaults.pathLossExponent
            it[Keys.REFERENCE_RSSI] = defaults.referenceRssiAt1m
        }
    }
}
