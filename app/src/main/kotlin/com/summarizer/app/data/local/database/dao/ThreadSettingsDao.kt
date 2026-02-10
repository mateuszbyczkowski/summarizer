package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.summarizer.app.data.local.entity.ThreadSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadSettingsDao {

    @Query("SELECT * FROM thread_settings WHERE threadId = :threadId")
    suspend fun getSettings(threadId: String): ThreadSettingsEntity?

    @Query("SELECT * FROM thread_settings WHERE threadId = :threadId")
    fun getSettingsFlow(threadId: String): Flow<ThreadSettingsEntity?>

    @Query("SELECT * FROM thread_settings")
    fun getAllSettings(): Flow<List<ThreadSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: ThreadSettingsEntity)

    @Update
    suspend fun update(settings: ThreadSettingsEntity)

    @Query("DELETE FROM thread_settings WHERE threadId = :threadId")
    suspend fun deleteSettings(threadId: String)

    @Query("UPDATE thread_settings SET lastSummarizedMessageTimestamp = :timestamp, lastSummarizedAt = :summarizedAt, updatedAt = :updatedAt WHERE threadId = :threadId")
    suspend fun updateLastSummarized(threadId: String, timestamp: Long, summarizedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE thread_settings SET summarizationMode = :mode, updatedAt = :updatedAt WHERE threadId = :threadId")
    suspend fun updateSummarizationMode(threadId: String, mode: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Get all threads that have auto-summarization enabled at a specific hour.
     * Includes threads with explicit hour setting and threads using global settings.
     */
    @Query("SELECT * FROM thread_settings WHERE summaryScheduleHour = :hour OR (summaryScheduleHour IS NULL AND autoSummarizationEnabled IS NULL) OR (summaryScheduleHour IS NULL AND autoSummarizationEnabled = 1)")
    suspend fun getThreadsForSchedule(hour: Int): List<ThreadSettingsEntity>
}
