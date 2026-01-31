package com.summarizer.app.data.repository

import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesForThread(threadId: String): Flow<List<Message>> {
        return messageDao.getMessagesForThread(threadId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getRecentMessagesForThread(threadId: String, limit: Int): List<Message> {
        return messageDao.getRecentMessagesForThread(threadId, limit).map { it.toDomainModel() }
    }

    override suspend fun saveMessage(message: Message) {
        messageDao.insert(message.toEntity())
    }

    override suspend fun getMessageCount(threadId: String): Int {
        return messageDao.getMessageCount(threadId)
    }

    override suspend fun deleteMessagesForThread(threadId: String) {
        messageDao.deleteMessagesForThread(threadId)
    }

    private fun MessageEntity.toDomainModel() = Message(
        id = id,
        threadId = threadId,
        threadName = threadName,
        sender = sender,
        content = content,
        timestamp = timestamp,
        createdAt = createdAt
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        threadId = threadId,
        threadName = threadName,
        sender = sender,
        content = content,
        timestamp = timestamp,
        createdAt = createdAt
    )
}
