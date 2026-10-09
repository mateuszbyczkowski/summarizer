package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
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

    /** Creates the thread unless it exists. Never REPLACE: that deletes the row and cascades to its settings. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(thread: ThreadEntity): Long

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    /**
     * Delete threads left without messages after retention cleanup. Threads that still have
     * summaries are kept so the summaries stay reachable.
     */
    @Query("DELETE FROM threads WHERE messageCount = 0 AND threadId NOT IN (SELECT DISTINCT threadId FROM summaries)")
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
            lastMessageTimestamp = COALESCE((SELECT MAX(timestamp) FROM messages WHERE messages.threadId = threads.threadId), 0)
    """)
    suspend fun refreshAllThreadStats()

    @Query("UPDATE threads SET isFollowed = :isFollowed WHERE threadId = :threadId")
    suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean)
}
