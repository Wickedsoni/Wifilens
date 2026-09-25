package com.wickedcoder.wifilens

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.wickedcoder.wifilens.core.database.WifiLensDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Migration test scaffold. Every shipped schema version is committed as JSON in
 * `core/database/schemas/`; this opens the real v1 schema, writes data the way v1 did, and checks that
 * it survives reopening under the current schema. When version 2 is added, put its migration in the
 * list passed to [MigrationTestHelper.runMigrationsAndValidate] and assert that the v1 rows come through.
 */
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WifiLensDatabase::class.java,
    )

    @Test
    fun version1SchemaOpensAndKeepsItsData() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO grid_plan (id, name, width, height) VALUES (1, 'Home', 5, 5)")
            execSQL("INSERT INTO room (id, planId, roomId, name) VALUES (1, 1, 1, 'Living room')")
            close()
        }

        // No migrations yet: reopening at the current version must validate against the exported schema.
        val db = helper.runMigrationsAndValidate(TEST_DB, CURRENT_VERSION, true)

        db.query("SELECT name FROM room WHERE planId = 1").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Living room", cursor.getString(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val CURRENT_VERSION = 1
    }
}
