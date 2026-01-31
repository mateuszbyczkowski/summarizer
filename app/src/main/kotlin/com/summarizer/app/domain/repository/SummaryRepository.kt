package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.Summary
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    fun getSummariesForThread(threadId: String): Flow<List<Summary>>
    suspend fun getLatestSummaryForThread(threadId: String): Summary?
    fun getRecentSummaries(limit: Int = 20): Flow<List<Summary>>
    suspend fun saveSummary(summary: Summary): Long
    suspend fun deleteSummariesForThread(threadId: String)
}
