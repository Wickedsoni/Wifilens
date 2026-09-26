package com.wickedcoder.wifilens.feature.more.presentation

import com.wickedcoder.wifilens.feature.more.domain.DeleteFloorPlan
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Use-case and ViewModel. The repository binding comes from `moreDataModule`. */
val moreModule = module {
    factory { DeleteFloorPlan(get()) }
    viewModel { SettingsViewModel(settingsRepository = get(), deleteFloorPlanUseCase = get()) }
}
