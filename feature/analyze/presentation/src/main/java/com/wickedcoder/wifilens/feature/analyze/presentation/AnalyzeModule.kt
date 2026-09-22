package com.wickedcoder.wifilens.feature.analyze.presentation

import com.wickedcoder.wifilens.core.wifi.currentWifiScanUpdate
import com.wickedcoder.wifilens.core.wifi.requestWifiScan
import com.wickedcoder.wifilens.core.wifi.wifiConnectionFlow
import com.wickedcoder.wifilens.core.wifi.wifiScanFlow
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val analyzeModule = module {
    viewModel {
        AnalyzeViewModel(
            wifiConnectionFlow = { wifiConnectionFlow(get()) },
            wifiScanFlow = { wifiScanFlow(get()) },
            startScan = { requestWifiScan(get()) },
            currentScanUpdate = { currentWifiScanUpdate(get()) },
        )
    }
}