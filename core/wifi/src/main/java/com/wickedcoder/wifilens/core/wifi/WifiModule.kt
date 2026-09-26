package com.wickedcoder.wifilens.core.wifi

import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Binds the Wi-Fi repository interfaces (in `:core:model`) to their device implementations. */
val wifiModule = module {
    single<WifiConnectionRepository> { WifiConnectionRepositoryImpl(androidContext()) }
    single<WifiScanRepository> { WifiScanRepositoryImpl(androidContext()) }
    single<SpeedTestRepository> { SpeedTestRepositoryImpl() }
}
