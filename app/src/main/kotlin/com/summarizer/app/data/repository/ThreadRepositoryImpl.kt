package com.summarizer.app.data.repository

import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.data.local.entity.ThreadEntity
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class ThreadRepositoryImpl @Inject constructor(
    private val threadDao: ThreadDao,
    private val messageDao: MessageDao
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

    override suspend fun mergeDuplicateThreads() {
        try {
            // Get all threads
            val threads = threadDao.getAllThreads().first()

            // Group threads by normalized name
            val threadGroups = threads.groupBy { thread ->
                normalizeThreadName(thread.threadName)
            }

            var mergedCount = 0

            // Process each group of threads with the same normalized name
            threadGroups.forEach { (normalizedName, duplicateThreads) ->
                if (duplicateThreads.size > 1) {
                    // Sort by most recent activity (to keep the most active thread)
                    val sorted = duplicateThreads.sortedByDescending { it.lastMessageTimestamp }
                    val primaryThread = sorted.first()
                    val duplicates = sorted.drop(1)

                    Timber.d("Found ${duplicates.size} duplicates for thread: ${primaryThread.threadName}")

                    // Merge duplicates into primary thread
                    duplicates.forEach { duplicate ->
                        // Update all messages from duplicate thread to primary thread
                        messageDao.updateThreadId(duplicate.threadId, primaryThread.threadId)

                        // Delete the duplicate thread
                        threadDao.deleteThread(duplicate.threadId)

                        mergedCount++
                        Timber.d("Merged thread ${duplicate.threadId} into ${primaryThread.threadId}")
                    }

                    // Update primary thread stats
                    val totalMessageCount = messageDao.getMessageCount(primaryThread.threadId)
                    threadDao.updateThreadStats(
                        primaryThread.threadId,
                        totalMessageCount,
                        primaryThread.lastMessageTimestamp
                    )
                }
            }

            if (mergedCount > 0) {
                Timber.i("Successfully merged $mergedCount duplicate threads")
            } else {
                Timber.d("No duplicate threads found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error merging duplicate threads: ${e.message}")
        }
    }

    private fun normalizeThreadName(threadName: String): String {
        // Same normalization as in WhatsAppNotificationListener
        var normalized = threadName.trim()

        // Remove message count suffix patterns like "(5 messages)", "(1 message)", etc.
        normalized = normalized.replace(Regex("\\s*\\(\\d+\\s+messages?\\)\\s*$", RegexOption.IGNORE_CASE), "")

        // Remove other common notification patterns
        normalized = normalized.replace(Regex("\\s*\\(\\d+\\s+new\\)\\s*$", RegexOption.IGNORE_CASE), "")
        normalized = normalized.replace(Regex("\\s*\\[\\d+\\s+messages?\\]\\s*$", RegexOption.IGNORE_CASE), "")

        return normalized
            .trim()                          // Remove leading/trailing whitespace again
            .replace(Regex("\\s+"), " ")     // Normalize multiple spaces to single space
            .lowercase()                      // Case-insensitive grouping
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
