package com.summarizer.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.summarizer.app.util.ThreadNaming

/** Schemas 4, 5 and 6 are identical. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add isFollowed column to threads table with default value true (1)
        db.execSQL("ALTER TABLE threads ADD COLUMN isFollowed INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create thread_settings table for per-thread summarization configuration
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS thread_settings (
                threadId TEXT PRIMARY KEY NOT NULL,
                summarizationMode TEXT NOT NULL DEFAULT 'INCREMENTAL',
                autoSummarizationEnabled INTEGER DEFAULT NULL,
                summaryScheduleHour INTEGER DEFAULT NULL,
                lastSummarizedMessageTimestamp INTEGER DEFAULT NULL,
                lastSummarizedAt INTEGER DEFAULT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY (threadId) REFERENCES threads(threadId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_thread_settings_threadId ON thread_settings(threadId)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Summaries now keep the overview paragraph, which used to be parsed and dropped
        db.execSQL("ALTER TABLE summaries ADD COLUMN overview TEXT NOT NULL DEFAULT ''")
        mergeDuplicateThreads(db)
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Structured summaries: key dates, which engine wrote it, and the user's rating.
        // Task due dates and done state live in the action_items JSON (old rows stay readable).
        db.execSQL("ALTER TABLE summaries ADD COLUMN key_dates TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE summaries ADD COLUMN engine TEXT")
        db.execSQL("ALTER TABLE summaries ADD COLUMN rating INTEGER")
    }
}

/** Every migration from a version that still has user data. Versions 1-3 predate releases. */
/** Latest-summary-per-thread lookups (Home, agenda) scanned the whole summaries table. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_summaries_threadId_generatedAt` ON `summaries` (`threadId`, `generatedAt`)")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
)

/**
 * One-time cleanup of threads created under older naming rules: every conversation is moved,
 * with its messages, summaries and settings, to its canonical [ThreadNaming.threadIdFor] ID.
 */
internal fun mergeDuplicateThreads(db: SupportSQLiteDatabase) {
    data class Row(val id: String, val name: String, val lastTimestamp: Long, val createdAt: Long, val followed: Boolean)

    val rows = db.query("SELECT threadId, threadName, lastMessageTimestamp, createdAt, isFollowed FROM threads").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(Row(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) != 0))
            }
        }
    }

    rows.groupBy { ThreadNaming.threadIdFor(it.name) }.forEach { (canonicalId, group) ->
        if (group.size == 1 && group.single().id == canonicalId) return@forEach

        if (group.none { it.id == canonicalId }) {
            val newest = group.maxBy { it.lastTimestamp }
            db.execSQL(
                "INSERT INTO threads (threadId, threadName, messageCount, lastMessageTimestamp, createdAt, isFollowed) VALUES (?, ?, 0, ?, ?, ?)",
                arrayOf<Any>(canonicalId, newest.name, newest.lastTimestamp, group.minOf { it.createdAt }, if (group.any { it.followed }) 1 else 0)
            )
        }

        group.filter { it.id != canonicalId }.forEach { duplicate ->
            val args = arrayOf<Any>(canonicalId, duplicate.id)
            db.execSQL("UPDATE OR IGNORE messages SET threadId = ? WHERE threadId = ?", args)
            db.execSQL("UPDATE summaries SET threadId = ? WHERE threadId = ?", args)
            db.execSQL("UPDATE OR IGNORE thread_settings SET threadId = ? WHERE threadId = ?", args)
            db.execSQL("DELETE FROM messages WHERE threadId = ?", arrayOf<Any>(duplicate.id))
            db.execSQL("DELETE FROM thread_settings WHERE threadId = ?", arrayOf<Any>(duplicate.id))
            db.execSQL("DELETE FROM threads WHERE threadId = ?", arrayOf<Any>(duplicate.id))
        }

        db.execSQL(
            """
            UPDATE threads SET
                messageCount = (SELECT COUNT(*) FROM messages WHERE threadId = ?1),
                lastMessageTimestamp = MAX(lastMessageTimestamp, COALESCE((SELECT MAX(timestamp) FROM messages WHERE threadId = ?1), 0))
            WHERE threadId = ?1
            """.trimIndent(),
            arrayOf<Any>(canonicalId)
        )
    }
}
