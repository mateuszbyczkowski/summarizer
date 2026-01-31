package com.summarizer.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val generatedAt: Long = System.currentTimeMillis()
)

@kotlinx.serialization.Serializable
data class ActionItem(
    val task: String,
    val deadline: String? = null,
    val mentionedBy: String? = null
)

@kotlinx.serialization.Serializable
data class ParticipantHighlight(
    val person: String,
    val message: String
)
