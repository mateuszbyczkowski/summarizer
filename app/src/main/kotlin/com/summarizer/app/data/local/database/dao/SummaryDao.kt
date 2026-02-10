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

    /**
     * Delete old summaries generated before the cutoff timestamp.
     */
    @Query("DELETE FROM summaries WHERE generatedAt < :cutoffTimestamp")
    suspend fun deleteSummariesBefore(cutoffTimestamp: Long)

    @Query("SELECT * FROM summaries WHERE key_topics LIKE '%' || :query || '%' OR action_items LIKE '%' || :query || '%' OR announcements LIKE '%' || :query || '%' OR threadName LIKE '%' || :query || '%' ORDER BY generatedAt DESC LIMIT 50")
    suspend fun searchSummaries(query: String): List<SummaryEntity>
}
