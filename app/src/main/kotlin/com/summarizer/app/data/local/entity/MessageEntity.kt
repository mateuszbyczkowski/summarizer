package com.summarizer.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

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
        /**
         * Identity of a message for deduplication. WhatsApp re-posts notifications with the same
         * messages and their original timestamps, so these fields identify a message exactly.
         */
        fun generateHash(threadId: String, sender: String, content: String, timestamp: Long): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(listOf(threadId, sender, content, timestamp.toString()).joinToString("\u0000").toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
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
