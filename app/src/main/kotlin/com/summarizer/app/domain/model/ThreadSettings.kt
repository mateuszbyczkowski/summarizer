package com.summarizer.app.domain.model

/**
 * Per-thread configuration for summarization behavior.
 *
 * @property threadId The unique identifier for the thread
 * @property summarizationMode Whether to summarize incrementally (new messages only) or fully (all messages)
 * @property autoSummarizationEnabled Whether auto-summarization is enabled for this thread (null = use global setting)
 * @property summaryScheduleHour Hour of day (0-23) when auto-summary should run (null = use global setting, -1 = disabled)
 * @property lastSummarizedMessageTimestamp Timestamp of the most recent message included in last summary (for incremental mode)
 * @property lastSummarizedAt Timestamp when the last summary was generated
 * @property createdAt When these settings were first created
 * @property updatedAt When these settings were last modified
 */
data class ThreadSettings(
    val threadId: String,
    val summarizationMode: SummarizationMode = SummarizationMode.INCREMENTAL,
    val autoSummarizationEnabled: Boolean? = null, // null = use global setting
    val summaryScheduleHour: Int? = null, // null = use global setting, -1 = disabled, 0-23 = specific hour
    val lastSummarizedMessageTimestamp: Long? = null,
    val lastSummarizedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
