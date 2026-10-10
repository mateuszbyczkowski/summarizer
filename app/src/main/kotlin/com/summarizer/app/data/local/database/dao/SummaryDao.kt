package com.summarizer.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT * FROM summaries WHERE id = :id")
    suspend fun getSummary(id: Long): SummaryEntity?

    /** The most recent summary of every thread that has one. */
    @Query("""
        SELECT * FROM summaries s
        WHERE s.id = (SELECT s2.id FROM summaries s2 WHERE s2.threadId = s.threadId ORDER BY s2.generatedAt DESC, s2.id DESC LIMIT 1)
    """)
    fun getLatestSummaries(): Flow<List<SummaryEntity>>

    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Update
    suspend fun update(summary: SummaryEntity)

    /** Ticks a task off (or back on); the tasks are stored as JSON, so the row is rewritten. */
    @Transaction
    suspend fun setActionItemDone(id: Long, index: Int, done: Boolean) {
        val summary = getSummary(id) ?: return
        if (index !in summary.actionItems.indices) return
        val items = summary.actionItems.toMutableList()
        items[index] = items[index].copy(done = done)
        update(summary.copy(actionItems = items))
    }

    @Query("UPDATE summaries SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int?)

    @Query("DELETE FROM summaries WHERE threadId = :threadId")
    suspend fun deleteSummariesForThread(threadId: String)

    /**
     * Delete old summaries generated before the cutoff timestamp.
     */

    @Query("SELECT * FROM summaries WHERE overview LIKE '%' || :query || '%' OR key_topics LIKE '%' || :query || '%' OR action_items LIKE '%' || :query || '%' OR announcements LIKE '%' || :query || '%' OR threadName LIKE '%' || :query || '%' ORDER BY generatedAt DESC LIMIT 50")
    suspend fun searchSummaries(query: String): List<SummaryEntity>
}
