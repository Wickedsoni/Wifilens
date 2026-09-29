package com.wickedcoder.wifilens

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.wickedcoder.wifilens.core.database.MIGRATION_1_2
import com.wickedcoder.wifilens.core.database.WifiLensDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Migration tests (ADR 0003). Every shipped schema is committed as JSON in `core/database/schemas/`; each test
 * opens a real old schema, writes data the way that version did, migrates, validates against the current schema
 * and asserts nothing was lost.
 */
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WifiLensDatabase::class.java,
    )

    /** A full v1 install: one plan with cells, rooms, a router pin and devices, exactly as v1 stored them. */
    private fun createVersion1Install() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO grid_plan (id, name, width, height) VALUES (1, 'Home', 2, 1)")
            execSQL("INSERT INTO cell (planId, x, y, cellTypeJson) VALUES (1, 0, 0, '{\"type\":\"floor\",\"roomId\":1}')")
            execSQL("INSERT INTO cell (planId, x, y, cellTypeJson) VALUES (1, 1, 0, '{\"type\":\"floor\",\"roomId\":2}')")
            execSQL("INSERT INTO room (id, planId, roomId, name) VALUES (1, 1, 1, 'Living room')")
            execSQL("INSERT INTO room (id, planId, roomId, name) VALUES (2, 1, 2, 'Kitchen')")
            execSQL("INSERT INTO router_pin (id, planId, x, y, band) VALUES (1, 1, 0, 0, '5')")
            execSQL("INSERT INTO device_pin (id, planId, x, y, name) VALUES (1, 1, 1, 0, 'Laptop')")
            execSQL("INSERT INTO device_pin (id, planId, x, y, name) VALUES (2, 1, 1, 0, 'TV')")
            close()
        }
    }

    @Test
    fun migrating1To2KeepsEveryV1Row() {
        createVersion1Install()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        fun count(sql: String) = db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, count("SELECT COUNT(*) FROM grid_plan WHERE id = 1 AND name = 'Home' AND width = 2 AND height = 1"))
        assertEquals(2, count("SELECT COUNT(*) FROM cell WHERE planId = 1"))
        assertEquals(2, count("SELECT COUNT(*) FROM room WHERE planId = 1"))
        assertEquals(1, count("SELECT COUNT(*) FROM router_pin WHERE planId = 1 AND x = 0 AND y = 0"))
        assertEquals(2, count("SELECT COUNT(*) FROM device_pin WHERE planId = 1"))
        db.query("SELECT cellTypeJson FROM cell WHERE x = 1").use {
            it.moveToFirst()
            assertEquals("{\"type\":\"floor\",\"roomId\":2}", it.getString(0))
        }
    }

    @Test
    fun migrating1To2BackfillsPlanTimestampsSoTheExistingPlanStaysActive() {
        createVersion1Install()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT createdAt, updatedAt, lastOpenedAt, calibrationPathLossExponent FROM grid_plan WHERE id = 1").use {
            it.moveToFirst()
            assertTrue("createdAt backfilled", it.getLong(0) > 0)
            assertTrue("updatedAt backfilled", it.getLong(1) > 0)
            assertTrue("lastOpenedAt backfilled", it.getLong(2) > 0)
            assertTrue("no calibration yet", it.isNull(3))
        }
    }

    @Test
    fun migrating1To2CreatesEmptyHistoryTablesAndKeepsCascades() {
        createVersion1Install()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        for (table in listOf("measurement", "scan_sample", "channel_congestion", "speed_test")) {
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals("$table empty", 0, it.getInt(0))
            }
        }
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO measurement (planId, x, y, bssid, rssiAvg, sampleCount, updatedAt) VALUES (1, 0, 0, 'aa', -50.0, 3, 1)")
        db.execSQL("DELETE FROM grid_plan WHERE id = 1")
        for (table in listOf("cell", "room", "router_pin", "device_pin", "measurement")) {
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals("$table cascaded", 0, it.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
