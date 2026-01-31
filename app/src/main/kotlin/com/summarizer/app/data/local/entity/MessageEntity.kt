package com.summarizer.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["threadId", "messageHash"], unique = true),
        Index(value = ["threadId", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val messageHash: String,
    val messageType: MessageType = MessageType.TEXT,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun generateHash(threadId: String, sender: String, content: String, timestamp: Long): String {
            return "${threadId}_${sender}_${content.take(100)}_${timestamp / 1000}".hashCode().toString()
        }
    }
}

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
