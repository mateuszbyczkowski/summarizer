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
}
