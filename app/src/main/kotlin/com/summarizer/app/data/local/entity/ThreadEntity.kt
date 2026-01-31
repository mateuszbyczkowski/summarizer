package com.summarizer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey
    val threadId: String,
    val threadName: String,
    val messageCount: Int = 0,
    val lastMessageTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)
