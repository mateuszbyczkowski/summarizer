package com.summarizer.app.domain.model

import com.summarizer.app.data.local.entity.ActionItem
import com.summarizer.app.data.local.entity.ParticipantHighlight

data class Summary(
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val keyTopics: List<String>,
    val actionItems: List<ActionItem>,
    val announcements: List<String>,
    val participantHighlights: List<ParticipantHighlight>,
    val messageCount: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val generatedAt: Long = System.currentTimeMillis()
)
