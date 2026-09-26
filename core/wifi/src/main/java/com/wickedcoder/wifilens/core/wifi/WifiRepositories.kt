package com.wickedcoder.wifilens.core.wifi

import android.content.Context
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import kotlinx.coroutines.flow.Flow

/** Real-device [WifiConnectionRepository]: a thin wrapper over [wifiConnectionFlow]. */
class WifiConnectionRepositoryImpl(private val context: Context) : WifiConnectionRepository {
    override fun observe(): Flow<WifiConnectionInfo> = wifiConnectionFlow(context)
}

/** Real-network [SpeedTestRepository]: a thin wrapper over [downloadSpeedFlow]. */
class SpeedTestRepositoryImpl : SpeedTestRepository {
    override fun run(): Flow<SpeedTestUpdate> = downloadSpeedFlow()
}

/** Real-device [WifiScanRepository]: thin wrappers over the scan functions in `WifiScanFlow.kt`. */
class WifiScanRepositoryImpl(private val context: Context) : WifiScanRepository {
    override fun observe(): Flow<WifiScanUpdate> = wifiScanFlow(context)

    override fun startScan(): Boolean = requestWifiScan(context)

    override fun currentUpdate(): WifiScanUpdate = currentWifiScanUpdate(context)
}
