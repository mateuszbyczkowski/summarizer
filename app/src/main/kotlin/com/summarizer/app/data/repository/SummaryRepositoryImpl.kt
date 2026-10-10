package com.summarizer.app.data.repository

import com.summarizer.app.data.local.database.dao.SummaryDao
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao
) : SummaryRepository {

    override fun getSummariesForThread(threadId: String): Flow<List<Summary>> {
        return summaryDao.getSummariesForThread(threadId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getLatestSummaryForThread(threadId: String): Summary? {
        return summaryDao.getLatestSummaryForThread(threadId)?.toDomainModel()
    }

    override suspend fun getSummary(summaryId: Long): Summary? {
        return summaryDao.getSummary(summaryId)?.toDomainModel()
    }

    override fun getRecentSummaries(limit: Int): Flow<List<Summary>> {
        return summaryDao.getRecentSummaries(limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveSummary(summary: Summary): Long {
        return summaryDao.insert(summary.toEntity())
    }

    override suspend fun setActionItemDone(summaryId: Long, index: Int, done: Boolean) {
        summaryDao.setActionItemDone(summaryId, index, done)
    }

    override suspend fun setRating(summaryId: Long, rating: Int?) {
        require(rating == null || rating == 1 || rating == -1) { "Rating must be +1, -1 or null" }
        summaryDao.setRating(summaryId, rating)
    }

    override suspend fun deleteSummariesForThread(threadId: String) {
        summaryDao.deleteSummariesForThread(threadId)
    }

    override suspend fun searchSummaries(query: String): List<Summary> {
        return summaryDao.searchSummaries(query).map { it.toDomainModel() }
    }
}
