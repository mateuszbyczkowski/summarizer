package com.summarizer.app.domain.model

data class Thread(
    val threadId: String,
    val threadName: String,
    val messageCount: Int = 0,
    val lastMessageTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isFollowed: Boolean = true
)
