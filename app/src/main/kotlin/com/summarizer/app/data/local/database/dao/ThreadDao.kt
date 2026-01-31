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

    @Query("SELECT * FROM threads ORDER BY lastMessageTimestamp DESC")
    fun getAllThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    suspend fun getThread(threadId: String): ThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: ThreadEntity)

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query("UPDATE threads SET messageCount = :count, lastMessageTimestamp = :timestamp WHERE threadId = :threadId")
    suspend fun updateThreadStats(threadId: String, count: Int, timestamp: Long)
}
