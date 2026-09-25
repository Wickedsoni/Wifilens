package com.wickedcoder.wifilens.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** The app's one Room database. The schema is exported to `core/database/schemas/` on every build and
 * committed: each shipped version's JSON is what migration tests open and what the next migration is
 * written against (see docs/adr/0003-room-migrations.md). */
@Database(
    entities = [
        GridPlanEntity::class,
        CellEntity::class,
        RoomEntity::class,
        DevicePinEntity::class,
        RouterPinEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CellTypeConverter::class)
abstract class WifiLensDatabase : RoomDatabase() {
    abstract fun gridPlanDao(): GridPlanDao

    abstract fun roomDao(): RoomDao

    abstract fun pinDao(): PinDao
}
