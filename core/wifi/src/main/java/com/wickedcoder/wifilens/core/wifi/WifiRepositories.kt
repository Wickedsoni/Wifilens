package com.wickedcoder.wifilens.core.wifi

import android.content.Context
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import kotlinx.coroutines.flow.Flow

/** Real-device [WifiConnectionRepository]: a thin wrapper over [wifiConnectionFlow]. */
class WifiConnectionRepositoryImpl(private val context: Context) : WifiConnectionRepository {
    override fun observe(): Flow<WifiConnectionInfo> = wifiConnectionFlow(context)
}

/** Real-network [SpeedTestRepository]: a thin wrapper over [downloadSpeedFlow]. */
class SpeedTestRepositoryImpl : SpeedTestRepository {
    override fun run(): Flow<SpeedTestUpdate> = downloadSpeedFlow()
}
