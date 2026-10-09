package com.summarizer.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summarizer.app.util.ThreadNaming
import org.junit.Assert.assertEquals
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
        helper.runMigrationsAndValidate(databaseName, 9, true, *ALL_MIGRATIONS).close()
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
}
