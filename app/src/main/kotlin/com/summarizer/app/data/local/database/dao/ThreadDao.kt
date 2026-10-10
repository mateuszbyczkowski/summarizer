package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.summarizer.app.data.local.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {

    @Query("SELECT * FROM threads ORDER BY lastMessageTimestamp DESC, threadName ASC")
    fun getAllThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE isFollowed = 1 ORDER BY lastMessageTimestamp DESC, threadName ASC")
    fun getFollowedThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    suspend fun getThread(threadId: String): ThreadEntity?

    /**
     * Every thread with the number of messages newer than its latest summary (all messages when
     * it has none) and its latest message. Uses the (threadId, timestamp) index on messages.
     */
    @Query("""
        SELECT t.*, (
            SELECT COUNT(*) FROM messages m
            WHERE m.threadId = t.threadId AND m.timestamp > COALESCE((
                SELECT s.endTimestamp FROM summaries s WHERE s.threadId = t.threadId
                ORDER BY s.generatedAt DESC, s.id DESC LIMIT 1
            ), -1)
        ) AS newSinceSummary, (
            SELECT m.sender FROM messages m WHERE m.threadId = t.threadId
            ORDER BY m.timestamp DESC, m.id DESC LIMIT 1
        ) AS lastMessageSender, (
            SELECT m.content FROM messages m WHERE m.threadId = t.threadId
            ORDER BY m.timestamp DESC, m.id DESC LIMIT 1
        ) AS lastMessageText
        FROM threads t
        ORDER BY t.lastMessageTimestamp DESC, t.threadName ASC
    """)
    fun getThreadsWithNewMessageCounts(): Flow<List<ThreadWithNewCount>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    fun observeThread(threadId: String): Flow<ThreadEntity?>

    /** Creates the thread unless it exists. Never REPLACE: that deletes the row and cascades to its settings. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(thread: ThreadEntity): Long

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    /**
     * Delete threads left without messages after retention cleanup. Threads that still have
     * summaries or per-thread settings are kept, so summaries stay reachable and choices survive.
     */
    @Query("""
        DELETE FROM threads WHERE messageCount = 0
        AND threadId NOT IN (SELECT DISTINCT threadId FROM summaries)
        AND threadId NOT IN (SELECT threadId FROM thread_settings)
    """)
    suspend fun deleteEmptyThreads(): Int

    @Query("""
        UPDATE threads SET
            messageCount = messageCount + 1,
            lastMessageTimestamp = MAX(lastMessageTimestamp, :timestamp),
            threadName = :threadName
        WHERE threadId = :threadId
    """)
    suspend fun recordNewMessage(threadId: String, threadName: String, timestamp: Long)

    /** Recompute message counts and last-message times from the messages table. */
    @Query("""
        UPDATE threads SET
            messageCount = (SELECT COUNT(*) FROM messages WHERE messages.threadId = threads.threadId),
            lastMessageTimestamp = COALESCE((SELECT MAX(timestamp) FROM messages WHERE messages.threadId = threads.threadId), lastMessageTimestamp)
    """)
    suspend fun refreshAllThreadStats()

    @Query("UPDATE threads SET isFollowed = :isFollowed WHERE threadId = :threadId")
    suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean)
}

/** A thread with the number of messages that arrived after its latest summary, and its latest message. */
data class ThreadWithNewCount(
    @Embedded val thread: ThreadEntity,
    val newSinceSummary: Int,
    val lastMessageSender: String?,
    val lastMessageText: String?
)
