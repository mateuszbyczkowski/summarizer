package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.model.ThreadOverview
import kotlinx.coroutines.flow.Flow

interface ThreadRepository {
    fun getAllThreads(): Flow<List<Thread>>
    fun getFollowedThreads(): Flow<List<Thread>>

    /** All threads (most recent first) with their latest summary, new messages and open tasks. */
    fun getThreadOverviews(): Flow<List<ThreadOverview>>
    suspend fun getThread(threadId: String): Thread?
    fun observeThread(threadId: String): Flow<Thread?>
    suspend fun updateThread(thread: Thread)
    suspend fun deleteThread(threadId: String)
    suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean)
}
