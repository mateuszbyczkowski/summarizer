package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Summary
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    fun getSummariesForThread(threadId: String): Flow<List<Summary>>
    suspend fun getLatestSummaryForThread(threadId: String): Summary?
    suspend fun getSummary(summaryId: Long): Summary?
    fun getRecentSummaries(limit: Int = 20): Flow<List<Summary>>
    suspend fun saveSummary(summary: Summary): Long

    /** Ticks the task at [index] of the summary's action items off, or back on. */
    suspend fun setActionItemDone(summaryId: Long, index: Int, done: Boolean)

    /** The user's feedback on a summary: +1, -1, or null to clear it. */
    suspend fun setRating(summaryId: Long, rating: Int?)
    suspend fun deleteSummariesForThread(threadId: String)
    suspend fun searchSummaries(query: String): List<Summary>
}
