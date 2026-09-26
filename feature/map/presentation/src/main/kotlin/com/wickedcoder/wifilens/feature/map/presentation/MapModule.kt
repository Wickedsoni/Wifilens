package com.wickedcoder.wifilens.feature.map.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule = module {
    viewModel { MapViewModel(repository = get(), savedStateHandle = get(), settingsRepository = get()) }
}
