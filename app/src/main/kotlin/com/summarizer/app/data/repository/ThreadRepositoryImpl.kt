package com.summarizer.app.data.repository

import com.summarizer.app.data.local.database.dao.SummaryDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.data.local.entity.ThreadEntity
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.model.ThreadOverview
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThreadRepositoryImpl @Inject constructor(
    private val threadDao: ThreadDao,
    private val summaryDao: SummaryDao,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ThreadRepository {

    /**
     * One shared overview query for all screens (Home, the Tasks badge, Tasks): it is the
     * heaviest query in the app and re-runs on every captured message. The cached value is
     * dropped when the query stops, so a later subscriber never starts from stale overviews.
     */
    private val sharedOverviews: Flow<List<ThreadOverview>> by lazy {
        overviewsFromDatabase().shareIn(
            applicationScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
            replay = 1
        )
    }

    override fun getAllThreads(): Flow<List<Thread>> {
        return threadDao.getAllThreads().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getFollowedThreads(): Flow<List<Thread>> {
        return threadDao.getFollowedThreads().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /** Two queries, each re-run by Room when its tables change, joined in memory. */
    override fun getThreadOverviews(): Flow<List<ThreadOverview>> = sharedOverviews

    private fun overviewsFromDatabase(): Flow<List<ThreadOverview>> =
        combine(
            threadDao.getThreadsWithNewMessageCounts(),
            summaryDao.getLatestSummaries()
        ) { threads, latestSummaries ->
            val summaryByThread = latestSummaries.associateBy({ it.threadId }, { it.toDomainModel() })
            threads.map { row ->
                val summary = summaryByThread[row.thread.threadId]
                ThreadOverview(
                    thread = row.thread.toDomainModel(),
                    latestSummary = summary,
                    newSinceSummary = row.newSinceSummary,
                    lastMessageSender = row.lastMessageSender,
                    lastMessageText = row.lastMessageText,
                    openTasks = summary?.actionItems?.count { !it.done } ?: 0
                )
            }
        }

    override suspend fun getThread(threadId: String): Thread? {
        return threadDao.getThread(threadId)?.toDomainModel()
    }

    override fun observeThread(threadId: String): Flow<Thread?> {
        return threadDao.observeThread(threadId).map { it?.toDomainModel() }
    }

    override suspend fun updateThread(thread: Thread) {
        threadDao.update(thread.toEntity())
    }

    override suspend fun deleteThread(threadId: String) {
        threadDao.deleteThread(threadId)
    }

    override suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean) {
        threadDao.updateFollowStatus(threadId, isFollowed)
    }

    private fun ThreadEntity.toDomainModel() = Thread(
        threadId = threadId,
        threadName = threadName,
        messageCount = messageCount,
        lastMessageTimestamp = lastMessageTimestamp,
        createdAt = createdAt,
        isFollowed = isFollowed
    )

    private fun Thread.toEntity() = ThreadEntity(
        threadId = threadId,
        threadName = threadName,
        messageCount = messageCount,
        lastMessageTimestamp = lastMessageTimestamp,
        createdAt = createdAt,
        isFollowed = isFollowed
    )
}
