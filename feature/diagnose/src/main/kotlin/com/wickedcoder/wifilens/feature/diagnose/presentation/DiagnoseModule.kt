package com.wickedcoder.wifilens.feature.diagnose.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val diagnoseModule = module {
    viewModel { DiagnoseViewModel(gridPlanDao = get(), pinDao = get(), roomDao = get(), settingsRepository = get()) }
}
