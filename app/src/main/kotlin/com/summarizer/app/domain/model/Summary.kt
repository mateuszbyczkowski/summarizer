package com.summarizer.app.domain.model

data class Summary(
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val overview: String = "",
    val keyTopics: List<String>,
    val actionItems: List<ActionItem>,
    val announcements: List<String>,
    val participantHighlights: List<ParticipantHighlight>,
    val messageCount: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val generatedAt: Long = System.currentTimeMillis(),
    val rawAIResponse: String? = null,  // Raw output from AI for debugging
    val keyDates: List<KeyDate> = emptyList(),
    /** Human-readable engine that wrote the summary, e.g. "Gemma 4 E2B · GPU" or "OpenAI gpt-4o-mini". */
    val engine: String? = null,
    /** The user's feedback: +1 (helpful), -1 (not helpful) or null (not rated). */
    val rating: Int? = null
)
