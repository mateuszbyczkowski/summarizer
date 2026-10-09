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

    /**
     * Whether a message stored before the SHA-256 dedup hash (legacy hashes are shorter) has the
     * same content around the same time. Lets re-posted notifications match pre-upgrade rows.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM messages
            WHERE threadId = :threadId AND content = :content AND LENGTH(messageHash) < 64
            AND timestamp BETWEEN :fromTimestamp AND :toTimestamp
        )
    """)
    suspend fun hasLegacyDuplicate(threadId: String, content: String, fromTimestamp: Long, toTimestamp: Long): Boolean

    /** Returns the new row ID, or -1 when the message was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)

    @Query("DELETE FROM messages WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteMessagesBefore(cutoffTimestamp: Long): Int

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' OR threadName LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 100")
    suspend fun searchMessages(query: String): List<MessageEntity>
}
