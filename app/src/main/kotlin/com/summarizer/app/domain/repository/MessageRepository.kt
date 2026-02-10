package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForThread(threadId: String): Flow<List<Message>>
    suspend fun getRecentMessagesForThread(threadId: String, limit: Int): List<Message>

    /**
     * Get messages for a thread since a specific timestamp (inclusive).
     * Used for incremental summarization.
     */
    suspend fun getMessagesForThreadSince(threadId: String, sinceTimestamp: Long): List<Message>

    suspend fun saveMessage(message: Message)
    suspend fun getMessageCount(threadId: String): Int
    suspend fun deleteMessagesForThread(threadId: String)
    suspend fun searchMessages(query: String): List<Message>
}
