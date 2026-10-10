package com.summarizer.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.util.ThreadNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val databaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun allMigrationsFromVersion4ProduceTheCurrentSchema() {
        helper.createDatabase(databaseName, 4).close()
        helper.runMigrationsAndValidate(databaseName, 10, true, *ALL_MIGRATIONS).close()
    }

    @Test
    fun migration8To9MergesLegacyThreadsIntoCanonicalId() {
        val canonicalId = ThreadNaming.threadIdFor("Family")
        helper.createDatabase(databaseName, 8).apply {
            execSQL("INSERT INTO threads VALUES ('legacy', 'Family (2 messages)', 1, 100, 1, 1)")
            execSQL("INSERT INTO threads VALUES ('$canonicalId', 'Family', 1, 200, 2, 0)")
            execSQL("INSERT INTO messages VALUES (1, 'legacy', 'Family', 'Anna', 'old', 100, 'h1', 'TEXT', 0, 1)")
            execSQL("INSERT INTO messages VALUES (2, '$canonicalId', 'Family', 'Anna', 'new', 200, 'h2', 'TEXT', 0, 2)")
            execSQL("INSERT INTO summaries VALUES (1, 'legacy', 'Family', '[]', '[]', '[]', '[]', 1, 100, 100, 150, NULL)")
            execSQL("INSERT INTO thread_settings VALUES ('legacy', 'FULL', NULL, NULL, NULL, NULL, 1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(databaseName, 9, true, MIGRATION_8_9)

        db.query("SELECT threadId, messageCount, lastMessageTimestamp FROM threads").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(canonicalId, c.getString(0))
            assertEquals(2, c.getInt(1))
            assertEquals(200L, c.getLong(2))
        }
        db.query("SELECT COUNT(*) FROM messages WHERE threadId = ?", arrayOf(canonicalId)).use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT threadId, overview FROM summaries").use { c ->
            c.moveToFirst()
            assertEquals(canonicalId, c.getString(0))
            assertEquals("", c.getString(1))
        }
        db.query("SELECT summarizationMode FROM thread_settings WHERE threadId = ?", arrayOf(canonicalId)).use { c ->
            c.moveToFirst()
            assertEquals("FULL", c.getString(0))
        }
        db.close()
    }

    @Test
    fun migration9To10AddsStructuredSummaryColumnsAndKeepsOldTasksReadable() {
        helper.createDatabase(databaseName, 9).apply {
            execSQL("INSERT INTO threads VALUES ('t', 'Family', 1, 100, 1, 1)")
            execSQL(
                "INSERT INTO summaries VALUES (1, 't', 'Family', 'Trip on Friday', '[\"Trip\"]', " +
                    "'[{\"task\":\"Pay 85 zł\",\"assignedTo\":null,\"priority\":\"medium\"}]', '[]', '[]', 1, 100, 100, 150, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(databaseName, 10, true, MIGRATION_9_10)

        db.query("SELECT key_dates, engine, rating, action_items FROM summaries WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("[]", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
            // Tasks stored before due dates and done state existed still decode, with defaults
            assertEquals(listOf(ActionItem(task = "Pay 85 zł")), Converters().toActionItemList(c.getString(3)))
        }
        db.close()
    }
}
