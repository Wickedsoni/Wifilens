package com.wickedcoder.wifilens.core.database

import android.content.Context
import androidx.room.Room
import com.wickedcoder.wifilens.core.model.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATABASE_NAME = "wifilens.db"

/** Hilt bindings for app storage: the [WifiLensDatabase] singleton and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WifiLensDatabase =
        Room.databaseBuilder(context, WifiLensDatabase::class.java, DATABASE_NAME).addMigrations(MIGRATION_1_2).build()

    @Provides
    fun provideGridPlanDao(database: WifiLensDatabase): GridPlanDao = database.gridPlanDao()

    @Provides
    fun provideRoomDao(database: WifiLensDatabase): RoomDao = database.roomDao()

    @Provides
    fun providePinDao(database: WifiLensDatabase): PinDao = database.pinDao()

    @Provides
    fun providePlanDao(database: WifiLensDatabase): PlanDao = database.planDao()

    @Provides
    fun provideMeasurementDao(database: WifiLensDatabase): MeasurementDao = database.measurementDao()

    @Provides
    fun provideHistoryDao(database: WifiLensDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideSpeedTestDao(database: WifiLensDatabase): SpeedTestDao = database.speedTestDao()
}

/** [SettingsRepository] is DataStore-backed but lives here as the same "app storage" concern. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
