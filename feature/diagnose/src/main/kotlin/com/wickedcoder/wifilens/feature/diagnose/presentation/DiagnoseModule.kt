package com.wickedcoder.wifilens.feature.diagnose.presentation

import com.wickedcoder.wifilens.core.wifi.downloadSpeedFlow
import com.wickedcoder.wifilens.core.wifi.wifiConnectionFlow
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val diagnoseModule = module {
    viewModel {
        DiagnoseViewModel(
            gridPlanDao = get(),
            pinDao = get(),
            roomDao = get(),
            settingsRepository = get(),
            wifiConnectionFlow = { wifiConnectionFlow(get()) },
            downloadSpeedFlow = { downloadSpeedFlow() },
        )
    }
}
