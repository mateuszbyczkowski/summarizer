package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.domain.model.ThreadSettings
import kotlinx.coroutines.flow.Flow

interface ThreadSettingsRepository {
    /**
     * Get settings for a specific thread. Returns null if no settings exist yet.
     */
    suspend fun getSettings(threadId: String): ThreadSettings?

    /**
     * Get settings as a Flow for reactive updates.
     */
    fun getSettingsFlow(threadId: String): Flow<ThreadSettings?>

    /**
     * Get all thread settings.
     */
    fun getAllSettings(): Flow<List<ThreadSettings>>

    /**
     * Save or update settings for a thread.
     */
    suspend fun saveSettings(settings: ThreadSettings)

    /**
     * Delete settings for a thread (will fall back to global defaults).
     */
    suspend fun deleteSettings(threadId: String)

    /**
     * Update the last summarized timestamp for incremental summarization.
     * Called after successfully generating a summary.
     */
    suspend fun updateLastSummarized(threadId: String, messageTimestamp: Long, summarizedAt: Long = System.currentTimeMillis())

    /**
     * Update the summarization mode for a thread.
     */
    suspend fun updateSummarizationMode(threadId: String, mode: SummarizationMode)

    /**
     * Get or create settings for a thread with default values.
     * Creates new settings with defaults if they don't exist.
     */
    suspend fun getOrCreateSettings(threadId: String): ThreadSettings

    /**
     * Get all threads that should be auto-summarized at a specific hour.
     */
    suspend fun getThreadsForSchedule(hour: Int): List<ThreadSettings>
}
