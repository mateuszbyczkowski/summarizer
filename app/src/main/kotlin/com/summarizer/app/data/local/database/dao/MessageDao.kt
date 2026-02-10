package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.summarizer.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForThread(threadId: String, limit: Int): List<MessageEntity>

    /**
     * Get messages for a thread since a specific timestamp (inclusive).
     * Used for incremental summarization.
     */
    @Query("SELECT * FROM messages WHERE threadId = :threadId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getMessagesForThreadSince(threadId: String, sinceTimestamp: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE messageHash = :messageHash LIMIT 1")
    suspend fun getMessageByHash(messageHash: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId")
    suspend fun getMessageCount(threadId: String): Int

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)

    @Query("DELETE FROM messages WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteMessagesBefore(cutoffTimestamp: Long)

    /**
     * Delete old messages for a thread, keeping at least the most recent N messages.
     * This ensures we never delete all messages from a thread even if they're old.
     */
    @Query("""
        DELETE FROM messages
        WHERE threadId = :threadId
        AND timestamp < :cutoffTimestamp
        AND messageHash NOT IN (
            SELECT messageHash FROM messages
            WHERE threadId = :threadId
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldMessagesForThread(threadId: String, cutoffTimestamp: Long, keepCount: Int)

    /**
     * Get all unique thread IDs that have messages.
     */
    @Query("SELECT DISTINCT threadId FROM messages")
    suspend fun getAllThreadIds(): List<String>

    @Query("UPDATE messages SET threadId = :newThreadId WHERE threadId = :oldThreadId")
    suspend fun updateThreadId(oldThreadId: String, newThreadId: String)

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' OR threadName LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 100")
    suspend fun searchMessages(query: String): List<MessageEntity>
}
