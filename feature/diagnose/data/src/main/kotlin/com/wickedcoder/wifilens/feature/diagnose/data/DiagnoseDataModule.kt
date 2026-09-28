package com.wickedcoder.wifilens.feature.diagnose.data

import com.wickedcoder.wifilens.feature.diagnose.domain.DiagnoseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Diagnose feature's repository interface to its Room-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DiagnoseDataModule {
    @Binds
    @Singleton
    abstract fun bindDiagnoseRepository(impl: DiagnoseRepositoryImpl): DiagnoseRepository
}
