package com.wickedcoder.wifilens.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GridPlanEntity::class,
        CellEntity::class,
        RoomEntity::class,
        DevicePinEntity::class,
        RouterPinEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CellTypeConverter::class)
/** The app's one Room database. `exportSchema = false` because this app has no shipped schema
 * history to diff against yet (single version, no migrations written). */
abstract class WifiLensDatabase : RoomDatabase() {
    abstract fun gridPlanDao(): GridPlanDao
    abstract fun roomDao(): RoomDao
    abstract fun pinDao(): PinDao
}
