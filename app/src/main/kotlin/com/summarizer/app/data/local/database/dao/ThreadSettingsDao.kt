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
    suspend fun getAllSettings(): List<ThreadSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: ThreadSettingsEntity)

    @Update
    suspend fun update(settings: ThreadSettingsEntity)

    @Query("DELETE FROM thread_settings WHERE threadId = :threadId")
    suspend fun deleteSettings(threadId: String)

    @Query("UPDATE thread_settings SET lastSummarizedMessageTimestamp = :timestamp, lastSummarizedAt = :summarizedAt, updatedAt = :updatedAt WHERE threadId = :threadId")
    suspend fun updateLastSummarized(threadId: String, timestamp: Long, summarizedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE thread_settings SET description = :description, updatedAt = :updatedAt WHERE threadId = :threadId")
    suspend fun updateDescription(threadId: String, description: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE thread_settings SET summarizationMode = :mode, updatedAt = :updatedAt WHERE threadId = :threadId")
    suspend fun updateSummarizationMode(threadId: String, mode: String, updatedAt: Long = System.currentTimeMillis())
}
