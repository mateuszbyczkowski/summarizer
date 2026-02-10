package com.summarizer.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thread_settings",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["threadId"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId"], unique = true)]
)
data class ThreadSettingsEntity(
    @PrimaryKey
    val threadId: String,
    val summarizationMode: String = "INCREMENTAL", // INCREMENTAL or FULL
    val autoSummarizationEnabled: Boolean? = null, // null = use global setting
    val summaryScheduleHour: Int? = null, // null = use global, -1 = disabled, 0-23 = specific hour
    val lastSummarizedMessageTimestamp: Long? = null,
    val lastSummarizedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
