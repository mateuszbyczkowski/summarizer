package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Thread
import kotlinx.coroutines.flow.Flow

interface ThreadRepository {
    fun getAllThreads(): Flow<List<Thread>>
    fun getFollowedThreads(): Flow<List<Thread>>
    suspend fun getThread(threadId: String): Thread?
    suspend fun saveThread(thread: Thread)
    suspend fun updateThread(thread: Thread)
    suspend fun deleteThread(threadId: String)
    suspend fun updateThreadStats(threadId: String, messageCount: Int, lastMessageTimestamp: Long)
    suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean)
    suspend fun mergeDuplicateThreads()
}
