package com.wickedcoder.wifilens

import android.app.Application
import com.wickedcoder.wifilens.core.database.databaseModule
import com.wickedcoder.wifilens.core.wifi.wifiModule
import com.wickedcoder.wifilens.feature.analyze.presentation.analyzeModule
import com.wickedcoder.wifilens.feature.diagnose.data.diagnoseDataModule
import com.wickedcoder.wifilens.feature.diagnose.presentation.diagnoseModule
import com.wickedcoder.wifilens.feature.map.data.mapDataModule
import com.wickedcoder.wifilens.feature.map.presentation.mapModule
import com.wickedcoder.wifilens.feature.more.data.moreDataModule
import com.wickedcoder.wifilens.feature.more.presentation.moreModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Starts Koin with every feature's DI module. Referenced from `AndroidManifest.xml`. */
class WifiLensApplication : Application() {
    override fun onCreate() {
        super.onCreate() // 1
        startKoin {
            // 2
            androidContext(this@WifiLensApplication) // 3
            modules( // 4
                databaseModule,
                wifiModule,
                analyzeModule,
                mapDataModule,
                mapModule,
                diagnoseDataModule,
                diagnoseModule,
                moreDataModule,
                moreModule,
            )
        }
    }
}
