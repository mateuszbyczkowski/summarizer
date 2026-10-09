package com.summarizer.app.data.repository

import androidx.room.withTransaction
import com.summarizer.app.data.local.database.AppDatabase
import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.data.local.entity.ThreadEntity
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MessageRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val messageDao: MessageDao,
    private val threadDao: ThreadDao
) : MessageRepository {

    override fun getMessagesForThread(threadId: String): Flow<List<Message>> {
        return messageDao.getMessagesForThread(threadId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getRecentMessagesForThread(threadId: String, limit: Int): List<Message> {
        return messageDao.getRecentMessagesForThread(threadId, limit).map { it.toDomainModel() }
    }

    override suspend fun getMessagesForThreadSince(threadId: String, sinceTimestamp: Long): List<Message> {
        return messageDao.getMessagesForThreadSince(threadId, sinceTimestamp).map { it.toDomainModel() }
    }

    override suspend fun saveIncomingMessage(message: Message): Boolean = database.withTransaction {
        threadDao.insertIfAbsent(
            ThreadEntity(
                threadId = message.threadId,
                threadName = message.threadName,
                lastMessageTimestamp = message.timestamp
            )
        )
        val inserted = messageDao.insert(message.toEntity()) != -1L
        if (inserted) {
            threadDao.recordNewMessage(message.threadId, message.threadName, message.timestamp)
        }
        inserted
    }

    override suspend fun deleteMessagesForThread(threadId: String) {
        messageDao.deleteMessagesForThread(threadId)
    }

    override suspend fun searchMessages(query: String): List<Message> {
        return messageDao.searchMessages(query).map { it.toDomainModel() }
    }

    private fun MessageEntity.toDomainModel() = Message(
        id = id,
        threadId = threadId,
        threadName = threadName,
        sender = sender,
        content = content,
        timestamp = timestamp,
        messageHash = messageHash,
        messageType = messageType,
        isDeleted = isDeleted,
        createdAt = createdAt
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        threadId = threadId,
        threadName = threadName,
        sender = sender,
        content = content,
        timestamp = timestamp,
        messageHash = messageHash,
        messageType = messageType,
        isDeleted = isDeleted,
        createdAt = createdAt
    )
}
