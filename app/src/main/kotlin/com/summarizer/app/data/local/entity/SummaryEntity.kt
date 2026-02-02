package com.summarizer.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.ParticipantHighlight

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: String,
    val threadName: String,

    @ColumnInfo(name = "key_topics")
    val keyTopics: List<String>,

    @ColumnInfo(name = "action_items")
    val actionItems: List<ActionItem>,

    @ColumnInfo(name = "announcements")
    val announcements: List<String>,

    @ColumnInfo(name = "participant_highlights")
    val participantHighlights: List<ParticipantHighlight>,

    val messageCount: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val generatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "raw_ai_response")
    val rawAIResponse: String? = null
)
