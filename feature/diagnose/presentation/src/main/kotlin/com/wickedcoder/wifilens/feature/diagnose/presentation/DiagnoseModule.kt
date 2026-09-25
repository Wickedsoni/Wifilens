package com.wickedcoder.wifilens.feature.diagnose.presentation

import com.wickedcoder.wifilens.feature.diagnose.domain.AnalyzeCoverage
import com.wickedcoder.wifilens.feature.diagnose.domain.FindBestRouterSpot
import com.wickedcoder.wifilens.feature.diagnose.domain.MoveRouter
import com.wickedcoder.wifilens.feature.diagnose.domain.ObservePlanContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Use-cases and the ViewModel. The repository binding comes from `diagnoseDataModule`. */
val diagnoseModule = module {
    factory { AnalyzeCoverage() }
    factory { FindBestRouterSpot() }
    factory { ObservePlanContext(get()) }
    factory { MoveRouter(get()) }
    viewModel {
        DiagnoseViewModel(
            observePlanContext = get(),
            settingsRepository = get(),
            connectionRepository = get(),
            speedTestRepository = get(),
            analyzeCoverage = get(),
            findBestRouterSpot = get(),
            moveRouter = get(),
        )
    }
}
