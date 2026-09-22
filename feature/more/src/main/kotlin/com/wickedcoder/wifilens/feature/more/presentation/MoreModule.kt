package com.wickedcoder.wifilens.feature.more.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val moreModule = module {
    viewModel { SettingsViewModel(settingsRepository = get(), gridPlanDao = get()) }
}
