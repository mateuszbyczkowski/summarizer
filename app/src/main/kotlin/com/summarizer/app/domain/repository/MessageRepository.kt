package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForThread(threadId: String): Flow<List<Message>>
    suspend fun getRecentMessagesForThread(threadId: String, limit: Int): List<Message>
    suspend fun saveMessage(message: Message)
    suspend fun getMessageCount(threadId: String): Int
    suspend fun deleteMessagesForThread(threadId: String)
}
