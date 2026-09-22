package com.wickedcoder.wifilens.feature.more.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.database.AppSettings
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.SettingsRepository
import com.wickedcoder.wifilens.core.database.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MVI ViewModel for the Settings screen: thin pass-through to [SettingsRepository] for every
 * preference, plus [deleteFloorPlan] (talks to [GridPlanDao] directly — clearing the plan isn't a
 * "setting" so much as the one destructive action Settings exposes). */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val gridPlanDao: GridPlanDao,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setTheme(theme: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    fun setHapticPaint(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticPaint(enabled) }
    fun setHapticConfirm(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticConfirm(enabled) }
    fun setHapticError(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticError(enabled) }
    fun setAutoScanEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoScanEnabled(enabled) }
    fun setPathLossExponent(value: Float) = viewModelScope.launch { settingsRepository.setPathLossExponent(value) }
    fun setReferenceRssiAt1m(value: Float) = viewModelScope.launch { settingsRepository.setReferenceRssiAt1m(value) }

    fun resetPredictionModel() = viewModelScope.launch { settingsRepository.resetPredictionModel() }

    fun deleteFloorPlan() = viewModelScope.launch {
        gridPlanDao.getActivePlan().first()?.plan?.let { gridPlanDao.deletePlan(it) }
    }
}
