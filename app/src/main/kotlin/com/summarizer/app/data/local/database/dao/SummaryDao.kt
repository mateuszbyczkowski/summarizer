package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.summarizer.app.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {

    @Query("SELECT * FROM summaries WHERE threadId = :threadId ORDER BY generatedAt DESC")
    fun getSummariesForThread(threadId: String): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE threadId = :threadId ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestSummaryForThread(threadId: String): SummaryEntity?

    @Query("SELECT * FROM summaries ORDER BY generatedAt DESC LIMIT :limit")
    fun getRecentSummaries(limit: Int = 20): Flow<List<SummaryEntity>>

    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Query("DELETE FROM summaries WHERE threadId = :threadId")
    suspend fun deleteSummariesForThread(threadId: String)
}
