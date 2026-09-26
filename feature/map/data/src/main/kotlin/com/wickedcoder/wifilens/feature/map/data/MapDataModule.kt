package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import org.koin.dsl.module

/** Binds the Map feature's repository interface to its Room-backed implementation. */
val mapDataModule = module {
    single<MapRepository> { MapRepositoryImpl(get(), get(), get(), get()) }
}
