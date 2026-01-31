package com.summarizer.app.data.repository

import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.data.local.entity.ThreadEntity
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThreadRepositoryImpl @Inject constructor(
    private val threadDao: ThreadDao
) : ThreadRepository {

    override fun getAllThreads(): Flow<List<Thread>> {
        return threadDao.getAllThreads().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getThread(threadId: String): Thread? {
        return threadDao.getThread(threadId)?.toDomainModel()
    }

    override suspend fun saveThread(thread: Thread) {
        threadDao.insert(thread.toEntity())
    }

    override suspend fun updateThread(thread: Thread) {
        threadDao.update(thread.toEntity())
    }

    override suspend fun deleteThread(threadId: String) {
        threadDao.deleteThread(threadId)
    }

    override suspend fun updateThreadStats(threadId: String, messageCount: Int, lastMessageTimestamp: Long) {
        threadDao.updateThreadStats(threadId, messageCount, lastMessageTimestamp)
    }

    private fun ThreadEntity.toDomainModel() = Thread(
        threadId = threadId,
        threadName = threadName,
        messageCount = messageCount,
        lastMessageTimestamp = lastMessageTimestamp,
        createdAt = createdAt
    )

    private fun Thread.toEntity() = ThreadEntity(
        threadId = threadId,
        threadName = threadName,
        messageCount = messageCount,
        lastMessageTimestamp = lastMessageTimestamp,
        createdAt = createdAt
    )
}
