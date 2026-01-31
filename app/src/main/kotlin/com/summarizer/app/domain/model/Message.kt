package com.summarizer.app.domain.model

data class Message(
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)
