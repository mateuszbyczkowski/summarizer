package com.summarizer.app.service

import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.util.ThreadNaming

/**
 * Platform-free view of a WhatsApp notification, extracted by [WhatsAppNotificationListener].
 */
data class NotificationSnapshot(
    val title: String?,
    val text: String?,
    val bigText: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val category: String?,
    /** Notification `when`: the time of the latest message, stable across re-posts. */
    val timestamp: Long,
    /** Messages from MessagingStyle; empty when the notification does not use it. */
    val messages: List<SnapshotMessage> = emptyList()
)

/** A MessagingStyle message; [sender] is null for messages sent by the device owner. */
data class SnapshotMessage(
    val sender: String?,
    val text: String?,
    val timestamp: Long
)

data class ParsedMessage(
    val threadName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val messageType: MessageType,
    val isDeleted: Boolean
)

/**
 * Turns WhatsApp notifications into chat messages.
 *
 * WhatsApp posts MessagingStyle notifications that carry the recent messages of a conversation
 * with their own senders and timestamps, and re-posts them whenever anything changes. Every
 * message is returned on every post; deduplication happens when storing them.
 */
object WhatsAppNotificationParser {

    private const val CATEGORY_MESSAGE = "msg"
    private const val SELF_SENDER = "You"
    private const val UNKNOWN_SENDER = "Unknown"

    private val SUMMARY_TEXT = Regex(
        "^\\d+\\s+(new\\s+)?messages?(\\s+from\\s+\\d+\\s+chats?)?$",
        RegexOption.IGNORE_CASE
    )

    private val DELETED_MESSAGES = setOf(
        "this message was deleted",
        "you deleted this message",
        "message deleted",
        "🚫 this message was deleted"
    )

    // Only phrases that cannot appear in normal conversation; misclassifying a real message
    // as SYSTEM would silently drop it from summaries.
    private val SYSTEM_MESSAGES = listOf(
        Regex("^.{1,80} created group .+$"),
        Regex("^.{1,80} changed the subject (from .+ )?to .+$"),
        Regex("^.{1,80} changed this group's icon$"),
        Regex("^.{1,80} changed the group description$"),
        Regex("^.{1,80} joined using this group's invite link$"),
        Regex("^You're now an admin$")
    )

    private val MEDIA_PREFIXES = listOf(
        "📷" to MessageType.IMAGE,
        "📹" to MessageType.VIDEO,
        "🎥" to MessageType.VIDEO,
        "🎵" to MessageType.AUDIO,
        "🎤" to MessageType.AUDIO,
        "📄" to MessageType.DOCUMENT,
        "📍" to MessageType.LOCATION,
        "👤" to MessageType.CONTACT
    )

    private val OMITTED_MEDIA = mapOf(
        "image omitted" to MessageType.IMAGE,
        "video omitted" to MessageType.VIDEO,
        "gif omitted" to MessageType.VIDEO,
        "audio omitted" to MessageType.AUDIO,
        "document omitted" to MessageType.DOCUMENT,
        "sticker omitted" to MessageType.STICKER
    )

    fun parse(notification: NotificationSnapshot): List<ParsedMessage> {
        if (notification.isGroupSummary || notification.isOngoing) return emptyList()

        return if (notification.messages.isNotEmpty()) {
            parseMessagingStyle(notification)
        } else {
            // Without MessagingStyle only real message notifications are trusted
            if (notification.category != CATEGORY_MESSAGE) return emptyList()
            listOfNotNull(parsePlain(notification))
        }
    }

    private fun parseMessagingStyle(notification: NotificationSnapshot): List<ParsedMessage> {
        val rawName = (if (notification.isGroupConversation) notification.conversationTitle else null)
            ?: notification.title
            ?: return emptyList()
        val threadName = ThreadNaming.displayName(rawName)
        if (threadName.isBlank()) return emptyList()

        return notification.messages.mapNotNull { message ->
            val content = message.text?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            val sender = message.sender?.trim()?.takeIf { it.isNotEmpty() } ?: SELF_SENDER
            buildMessage(threadName, sender, content, message.timestamp)
        }
    }

    private fun parsePlain(notification: NotificationSnapshot): ParsedMessage? {
        val title = notification.title?.trim().orEmpty()
        val content = (notification.bigText ?: notification.text)?.trim().orEmpty()
        if (title.isEmpty() || content.isEmpty() || SUMMARY_TEXT.matches(content)) return null

        // Older WhatsApp versions title group messages "Group: Sender"; 1:1 chats are titled
        // with the contact name. The message text is never split, since it may contain colons.
        val separator = title.indexOf(": ")
        val (rawThreadName, sender) = when {
            !notification.isGroupConversation -> title to title
            separator > 0 -> title.substring(0, separator) to title.substring(separator + 2)
            else -> title to UNKNOWN_SENDER
        }
        val threadName = ThreadNaming.displayName(rawThreadName)
        if (threadName.isBlank()) return null

        return buildMessage(threadName, ThreadNaming.displayName(sender), content, notification.timestamp)
    }

    private fun buildMessage(threadName: String, sender: String, content: String, timestamp: Long): ParsedMessage {
        val type = detectMessageType(content)
        return ParsedMessage(
            threadName = threadName,
            sender = sender,
            content = content,
            timestamp = timestamp,
            messageType = type,
            isDeleted = type == MessageType.DELETED
        )
    }

    fun detectMessageType(content: String): MessageType {
        val normalized = content.trim()
        val lower = normalized.lowercase()
        return when {
            lower in DELETED_MESSAGES -> MessageType.DELETED
            SYSTEM_MESSAGES.any { it.matches(normalized) } -> MessageType.SYSTEM
            else -> OMITTED_MEDIA.entries.firstOrNull { lower == it.key || lower.endsWith("<${it.key}>") }?.value
                ?: MEDIA_PREFIXES.firstOrNull { normalized.startsWith(it.first) }?.second
                ?: MessageType.TEXT
        }
    }
}
