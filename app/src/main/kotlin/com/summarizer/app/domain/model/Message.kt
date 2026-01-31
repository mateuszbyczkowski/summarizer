package com.summarizer.app.domain.model

data class Message(
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val messageHash: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    DOCUMENT,
    AUDIO,
    LOCATION,
    CONTACT,
    STICKER,
    DELETED,
    SYSTEM,
    UNKNOWN
}
