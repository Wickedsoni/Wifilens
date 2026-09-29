package com.wickedcoder.wifilens.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (Sprint 6, ADR 0007): additive only, so no v1 data is touched.
 * - grid_plan gains timestamps (the active plan = most recently opened) and nullable calibration columns.
 *   Existing plans get "now" for all three timestamps, so the user's plan stays the active one.
 * - New, empty tables for measurements (Sprint 7), scan history, channel roll-ups and speed tests (Sprint 8).
 * Statements are copied from the exported schema 2.json, so the result validates against Room's expectations.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `grid_plan` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `grid_plan` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `grid_plan` ADD COLUMN `lastOpenedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `grid_plan` ADD COLUMN `calibrationReferenceRssi` REAL")
        db.execSQL("ALTER TABLE `grid_plan` ADD COLUMN `calibrationPathLossExponent` REAL")
        db.execSQL("ALTER TABLE `grid_plan` ADD COLUMN `calibrationRmseDb` REAL")
        db.execSQL(
            "UPDATE `grid_plan` SET `createdAt` = CAST(strftime('%s','now') AS INTEGER) * 1000, " +
                "`updatedAt` = CAST(strftime('%s','now') AS INTEGER) * 1000, " +
                "`lastOpenedAt` = CAST(strftime('%s','now') AS INTEGER) * 1000",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`planId` INTEGER NOT NULL, `x` INTEGER NOT NULL, `y` INTEGER NOT NULL, `bssid` TEXT NOT NULL, " +
                "`rssiAvg` REAL NOT NULL, `sampleCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`planId`) REFERENCES `grid_plan`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_planId` ON `measurement` (`planId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_planId_x_y_bssid` ON `measurement` (`planId`, `x`, `y`, `bssid`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_sample` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bssid` TEXT NOT NULL, `ssid` TEXT NOT NULL, `rssi` INTEGER NOT NULL, `channel` INTEGER NOT NULL, " +
                "`band` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_sample_timestamp` ON `scan_sample` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_sample_bssid` ON `scan_sample` (`bssid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `channel_congestion` (`hourStart` INTEGER NOT NULL, `band` TEXT NOT NULL, " +
                "`channel` INTEGER NOT NULL, `networkCount` INTEGER NOT NULL, `strongestRssi` INTEGER NOT NULL, " +
                "`congestionScore` INTEGER NOT NULL, PRIMARY KEY(`hourStart`, `band`, `channel`))",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `speed_test` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `downloadMbps` REAL NOT NULL, `linkSpeedMbps` INTEGER, `rssi` INTEGER, " +
                "`frequencyMhz` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_speed_test_timestamp` ON `speed_test` (`timestamp`)")
    }
}
