package com.wickedcoder.wifilens.feature.more.data

import com.wickedcoder.wifilens.feature.more.domain.FloorPlanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the More feature's repository interface to its Room-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class MoreDataModule {
    @Binds
    @Singleton
    abstract fun bindFloorPlanRepository(impl: FloorPlanRepositoryImpl): FloorPlanRepository
}
