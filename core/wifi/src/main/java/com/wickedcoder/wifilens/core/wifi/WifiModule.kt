package com.wickedcoder.wifilens.core.wifi

import com.wickedcoder.wifilens.core.model.SpeedTestRepository
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Wi-Fi repository interfaces (in `:core:model`) to their device implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WifiModule {
    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: WifiConnectionRepositoryImpl): WifiConnectionRepository

    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: WifiScanRepositoryImpl): WifiScanRepository

    @Binds
    @Singleton
    abstract fun bindSpeedTestRepository(impl: SpeedTestRepositoryImpl): SpeedTestRepository
}
