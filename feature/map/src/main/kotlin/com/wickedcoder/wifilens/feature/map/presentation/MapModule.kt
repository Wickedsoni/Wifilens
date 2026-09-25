package com.wickedcoder.wifilens.feature.map.presentation

import com.wickedcoder.wifilens.feature.map.data.MapRepositoryImpl
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule = module {
    single<MapRepository> { MapRepositoryImpl(get(), get(), get(), get()) }
    viewModel { MapViewModel(repository = get(), savedStateHandle = get(), settingsRepository = get()) }
}
