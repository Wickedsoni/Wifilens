package com.wickedcoder.wifilens.feature.analyze.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ViewModel. The Wi-Fi repositories it uses are bound by `wifiModule` in `:core:wifi`. */
val analyzeModule = module {
    viewModel {
        AnalyzeViewModel(
            scanRepository = get(),
            connectionRepository = get(),
        )
    }
}
