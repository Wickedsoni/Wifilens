package com.wickedcoder.wifilens.core.database

import androidx.room.Room
import org.koin.dsl.module

private const val DATABASE_NAME = "wifilens.db"

/** Koin module for the database layer: the [WifiLensDatabase] singleton, its three DAOs, and
 * [SettingsRepository] (DataStore-backed, but registered here since it's the same "app storage"
 * concern and every screen that needs settings already depends on this module). */
val databaseModule = module {
    single {
        Room.databaseBuilder(get(), WifiLensDatabase::class.java, DATABASE_NAME).build()
    }
    single { get<WifiLensDatabase>().gridPlanDao() }
    single { get<WifiLensDatabase>().roomDao() }
    single { get<WifiLensDatabase>().pinDao() }
    single { TransactionRunner(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
}
