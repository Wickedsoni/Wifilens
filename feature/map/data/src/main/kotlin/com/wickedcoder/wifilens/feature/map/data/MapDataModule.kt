package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.PlanRepository
import com.wickedcoder.wifilens.feature.map.domain.SurveyRepository
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

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindSurveyRepository(impl: SurveyRepositoryImpl): SurveyRepository
}
