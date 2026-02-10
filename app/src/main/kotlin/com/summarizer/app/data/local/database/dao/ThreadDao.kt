package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.summarizer.app.data.local.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {

    @Query("SELECT * FROM threads ORDER BY lastMessageTimestamp DESC, threadName ASC")
    fun getAllThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE isFollowed = 1 ORDER BY lastMessageTimestamp DESC, threadName ASC")
    fun getFollowedThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    suspend fun getThread(threadId: String): ThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: ThreadEntity)

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    /**
     * Delete threads that have no messages (messageCount = 0).
     * This cleans up orphaned threads after data retention cleanup.
     */
    @Query("DELETE FROM threads WHERE messageCount = 0")
    suspend fun deleteEmptyThreads(): Int

    @Query("UPDATE threads SET messageCount = :count, lastMessageTimestamp = :timestamp WHERE threadId = :threadId")
    suspend fun updateThreadStats(threadId: String, count: Int, timestamp: Long)

    @Query("UPDATE threads SET isFollowed = :isFollowed WHERE threadId = :threadId")
    suspend fun updateFollowStatus(threadId: String, isFollowed: Boolean)
}
