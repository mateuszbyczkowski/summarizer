package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Thread
import kotlinx.coroutines.flow.Flow

interface ThreadRepository {
    fun getAllThreads(): Flow<List<Thread>>
    fun getFollowedThreads(): Flow<List<Thread>>
    suspend fun getThread(threadId: String): Thread?
    fun observeThread(threadId: String): Flow<Thread?>
    suspend fun updateThread(thread: Thread)
    suspend fun deleteThread(threadId: String)
    suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean)
}
