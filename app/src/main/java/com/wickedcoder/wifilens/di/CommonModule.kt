package com.wickedcoder.wifilens.di

import com.wickedcoder.wifilens.core.common.ApplicationScope
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.common.DefaultDispatcher
import com.wickedcoder.wifilens.core.common.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** The only place `Dispatchers.*` is named: everything else injects a qualifier, so tests can swap in test dispatchers. */
@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun provideClock(): Clock = Clock(System::currentTimeMillis)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
