package com.wickedcoder.wifilens.feature.more.data

import com.wickedcoder.wifilens.feature.more.domain.FloorPlanRepository
import org.koin.dsl.module

/** Binds the More feature's repository interface to its Room-backed implementation. */
val moreDataModule = module {
    single<FloorPlanRepository> { FloorPlanRepositoryImpl(gridPlanDao = get()) }
}
