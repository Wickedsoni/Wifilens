package com.wickedcoder.wifilens.feature.diagnose.data

import com.wickedcoder.wifilens.feature.diagnose.domain.DiagnoseRepository
import org.koin.dsl.module

/** Binds Diagnose's repository interface to its Room-backed implementation. */
val diagnoseDataModule = module {
    single<DiagnoseRepository> { DiagnoseRepositoryImpl(gridPlanDao = get(), pinDao = get(), roomDao = get()) }
}
