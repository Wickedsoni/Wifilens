package com.wickedcoder.wifilens.core.wifi

import android.content.Context
import com.wickedcoder.wifilens.core.common.IoDispatcher
import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/** Real-device [WifiConnectionRepository]: a thin wrapper over [wifiConnectionFlow]. */
class WifiConnectionRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WifiConnectionRepository {
        override fun observe(): Flow<WifiConnectionInfo> = wifiConnectionFlow(context)

        override fun observeLive(periodMillis: Long): Flow<WifiConnectionInfo> =
            wifiConnectionPollFlow(context, periodMillis).flowOn(ioDispatcher) // every poll counts: the survey averages them
    }

/** Real-network [SpeedTestRepository]: a thin wrapper over [downloadSpeedFlow]. */
class SpeedTestRepositoryImpl
    @Inject
    constructor(
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SpeedTestRepository {
        override fun run(): Flow<SpeedTestUpdate> = downloadSpeedFlow(ioDispatcher = ioDispatcher)
    }

/** Real-device [WifiScanRepository]: thin wrappers over the scan functions in `WifiScanFlow.kt`. */
class WifiScanRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WifiScanRepository {
        override fun observe(): Flow<WifiScanUpdate> = wifiScanFlow(context)

        override fun startScan(): Boolean = requestWifiScan(context)

        override fun currentUpdate(): WifiScanUpdate = currentWifiScanUpdate(context)
    }
