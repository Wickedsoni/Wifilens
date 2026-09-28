package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Map feature's repository interface to its Room-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class MapDataModule {
    @Binds
    @Singleton
    abstract fun bindMapRepository(impl: MapRepositoryImpl): MapRepository
}
