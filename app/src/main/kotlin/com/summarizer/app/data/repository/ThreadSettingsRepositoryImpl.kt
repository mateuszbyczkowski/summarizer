package com.summarizer.app.data.repository

import com.summarizer.app.data.local.database.dao.ThreadSettingsDao
import com.summarizer.app.data.local.entity.ThreadSettingsEntity
import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.domain.model.ThreadSettings
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThreadSettingsRepositoryImpl @Inject constructor(
    private val threadSettingsDao: ThreadSettingsDao
) : ThreadSettingsRepository {

    override suspend fun getSettings(threadId: String): ThreadSettings? {
        return threadSettingsDao.getSettings(threadId)?.toDomainModel()
    }

    override fun getSettingsFlow(threadId: String): Flow<ThreadSettings?> {
        return threadSettingsDao.getSettingsFlow(threadId).map { entity ->
            entity?.toDomainModel()
        }
    }

    override fun getAllSettings(): Flow<List<ThreadSettings>> {
        return threadSettingsDao.getAllSettings().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveSettings(settings: ThreadSettings) {
        threadSettingsDao.insert(settings.toEntity())
    }

    override suspend fun deleteSettings(threadId: String) {
        threadSettingsDao.deleteSettings(threadId)
    }

    override suspend fun updateLastSummarized(threadId: String, messageTimestamp: Long, summarizedAt: Long) {
        threadSettingsDao.updateLastSummarized(threadId, messageTimestamp, summarizedAt)
    }

    override suspend fun updateSummarizationMode(threadId: String, mode: SummarizationMode) {
        threadSettingsDao.updateSummarizationMode(threadId, mode.name)
    }

    override suspend fun getOrCreateSettings(threadId: String): ThreadSettings {
        return getSettings(threadId) ?: ThreadSettings(threadId = threadId).also {
            saveSettings(it)
        }
    }

    override suspend fun getThreadsForSchedule(hour: Int): List<ThreadSettings> {
        return threadSettingsDao.getThreadsForSchedule(hour).map { it.toDomainModel() }
    }

    private fun ThreadSettingsEntity.toDomainModel() = ThreadSettings(
        threadId = threadId,
        summarizationMode = SummarizationMode.valueOf(summarizationMode),
        autoSummarizationEnabled = autoSummarizationEnabled,
        summaryScheduleHour = summaryScheduleHour,
        lastSummarizedMessageTimestamp = lastSummarizedMessageTimestamp,
        lastSummarizedAt = lastSummarizedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ThreadSettings.toEntity() = ThreadSettingsEntity(
        threadId = threadId,
        summarizationMode = summarizationMode.name,
        autoSummarizationEnabled = autoSummarizationEnabled,
        summaryScheduleHour = summaryScheduleHour,
        lastSummarizedMessageTimestamp = lastSummarizedMessageTimestamp,
        lastSummarizedAt = lastSummarizedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
