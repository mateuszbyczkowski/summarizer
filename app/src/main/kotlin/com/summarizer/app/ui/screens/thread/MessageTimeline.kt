package com.summarizer.app.ui.screens.thread

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One row of the messages tab. */
sealed interface TimelineItem {
    /** Stable key for lazy lists. */
    val key: String

    /** Day header before the first message of each day. */
    data class DateSeparator(val date: LocalDate) : TimelineItem {
        override val key: String get() = "date-$date"
    }

    /** "New since summary", before the first message newer than the latest summary. */
    data object NewDivider : TimelineItem {
        override val key: String get() = "new-divider"
    }

    /**
     * A message. Consecutive messages from one sender form a group: the sender's name and avatar
     * show on the first and the bubbles sit closer together.
     */
    data class Bubble(
        val message: Message,
        val fromMe: Boolean,
        val firstInGroup: Boolean,
        val lastInGroup: Boolean
    ) : TimelineItem {
        override val key: String get() = "message-${message.id}"
        val isSystem: Boolean get() = message.messageType == MessageType.SYSTEM
    }
}

/** Builds the messages tab from a thread's messages. */
object MessageTimeline {

    /**
     * @param messages The thread's messages, oldest first
     * @param newSince Messages newer than this (the latest summary's last message) are new;
     *        null when there is no summary, which shows no divider
     * @param isMine Whether the user sent a message
     */
    fun build(
        messages: List<Message>,
        newSince: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
        isMine: (Message) -> Boolean
    ): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        var day: LocalDate? = null
        var dividerPlaced = false

        messages.forEachIndexed { index, message ->
            val date = Instant.ofEpochMilli(message.timestamp).atZone(zone).toLocalDate()
            val isNew = newSince != null && message.timestamp > newSince
            if (date != day) {
                items += TimelineItem.DateSeparator(date)
                day = date
            }
            if (isNew && !dividerPlaced) {
                items += TimelineItem.NewDivider
                dividerPlaced = true
            }
            val previous = items.last() as? TimelineItem.Bubble
            val next = messages.getOrNull(index + 1)
            items += TimelineItem.Bubble(
                message = message,
                fromMe = isMine(message),
                firstInGroup = previous == null || !sameGroup(previous.message, message),
                lastInGroup = next == null || !sameGroup(message, next) ||
                    Instant.ofEpochMilli(next.timestamp).atZone(zone).toLocalDate() != date ||
                    (newSince != null && !dividerPlaced && next.timestamp > newSince)
            )
        }
        return items
    }

    /** Index of the row to show first: the "new" divider, else the last row. */
    fun initialScrollIndex(items: List<TimelineItem>): Int {
        val divider = items.indexOf(TimelineItem.NewDivider)
        return if (divider >= 0) divider else items.lastIndex.coerceAtLeast(0)
    }

    /**
     * The caption of a media message, without WhatsApp's placeholder: "📷 Photo" has none,
     * "📷 Our class trip" has "Our class trip". Null for plain text messages.
     */
    fun mediaCaption(message: Message): String? {
        if (message.messageType !in MEDIA_TYPES) return null
        val text = message.content.trim().trimStart { !it.isLetterOrDigit() }.trim()
        return text.takeUnless { it.isEmpty() || MEDIA_PLACEHOLDER.matches(it) }
    }

    private val MEDIA_TYPES = setOf(
        MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO, MessageType.DOCUMENT,
        MessageType.LOCATION, MessageType.CONTACT, MessageType.STICKER
    )

    private val MEDIA_PLACEHOLDER = Regex(
        "(?:photo|image|video|gif|audio|voice message|document|location|live location|contact|sticker)",
        RegexOption.IGNORE_CASE
    )

    private fun sameGroup(first: Message, second: Message): Boolean =
        first.sender == second.sender &&
            first.messageType != MessageType.SYSTEM &&
            second.messageType != MessageType.SYSTEM
}
