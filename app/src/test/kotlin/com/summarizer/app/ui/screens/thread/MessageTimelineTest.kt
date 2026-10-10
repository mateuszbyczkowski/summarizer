package com.summarizer.app.ui.screens.thread

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.ui.screens.thread.TimelineItem.Bubble
import com.summarizer.app.ui.screens.thread.TimelineItem.DateSeparator
import com.summarizer.app.ui.screens.thread.TimelineItem.NewDivider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MessageTimelineTest {

    private val zone = ZoneOffset.UTC
    private val day1 = LocalDate.of(2026, 10, 12)
    private val day2 = LocalDate.of(2026, 10, 13)

    private var nextId = 1L

    private fun message(
        sender: String,
        day: LocalDate,
        hour: Int,
        minute: Int = 0,
        type: MessageType = MessageType.TEXT,
        content: String = "text"
    ) = Message(
        id = nextId++,
        threadId = "t",
        threadName = "Chat",
        sender = sender,
        content = content,
        timestamp = day.atTime(hour, minute).toInstant(zone).toEpochMilli(),
        messageType = type
    )

    private fun build(messages: List<Message>, newSince: Long? = null) =
        MessageTimeline.build(messages, newSince, zone) { it.sender == "You" }

    private fun List<TimelineItem>.shape(): List<String> = map { item ->
        when (item) {
            is DateSeparator -> "date ${item.date}"
            NewDivider -> "new"
            is Bubble -> buildString {
                append(item.message.sender)
                if (item.firstInGroup) append(" first")
                if (item.lastInGroup) append(" last")
            }
        }
    }

    @Test
    fun `consecutive messages from one sender form a group`() {
        val items = build(
            listOf(
                message("Anna", day1, 9),
                message("Anna", day1, 9, 1),
                message("Anna", day1, 9, 2),
                message("Marek", day1, 10)
            )
        )
        assertEquals(
            listOf("date $day1", "Anna first", "Anna", "Anna last", "Marek first last"),
            items.shape()
        )
    }

    @Test
    fun `a new day starts a separator and a new group`() {
        val items = build(listOf(message("Anna", day1, 22), message("Anna", day2, 7)))
        assertEquals(
            listOf("date $day1", "Anna first last", "date $day2", "Anna first last"),
            items.shape()
        )
    }

    @Test
    fun `the divider goes before the first message newer than the summary and splits a group`() {
        val summarized = message("Anna", day1, 9)
        val items = build(
            listOf(summarized, message("Anna", day1, 10), message("Marek", day1, 11)),
            newSince = summarized.timestamp
        )
        assertEquals(
            listOf("date $day1", "Anna first last", "new", "Anna first last", "Marek first last"),
            items.shape()
        )
        assertEquals(2, MessageTimeline.initialScrollIndex(items))
    }

    @Test
    fun `the divider follows the day separator when the new messages start a day`() {
        val summarized = message("Anna", day1, 9)
        val items = build(listOf(summarized, message("Anna", day2, 9)), newSince = summarized.timestamp)
        assertEquals(listOf("date $day1", "Anna first last", "date $day2", "new", "Anna first last"), items.shape())
    }

    @Test
    fun `no divider without a summary or without new messages`() {
        val messages = listOf(message("Anna", day1, 9), message("Marek", day1, 10))
        assertTrue(NewDivider !in build(messages, newSince = null))
        assertTrue(NewDivider !in build(messages, newSince = messages.last().timestamp))
        assertEquals(build(messages).lastIndex, MessageTimeline.initialScrollIndex(build(messages)))
    }

    @Test
    fun `system messages never join a group`() {
        val items = build(
            listOf(
                message("Anna", day1, 9),
                message("Anna", day1, 9, 1, type = MessageType.SYSTEM),
                message("Anna", day1, 9, 2)
            )
        )
        assertEquals(listOf("date $day1", "Anna first last", "Anna first last", "Anna first last"), items.shape())
        assertTrue((items[2] as Bubble).isSystem)
    }

    @Test
    fun `own messages are marked`() {
        val items = build(listOf(message("You", day1, 9), message("Anna", day1, 10)))
        val bubbles = items.filterIsInstance<Bubble>()
        assertEquals(listOf(true, false), bubbles.map { it.fromMe })
    }

    @Test
    fun `empty thread gives an empty timeline`() {
        val items = build(emptyList())
        assertTrue(items.isEmpty())
        assertEquals(0, MessageTimeline.initialScrollIndex(items))
    }

    @Test
    fun `media captions drop the placeholder`() {
        assertNull(MessageTimeline.mediaCaption(message("A", day1, 9, type = MessageType.IMAGE, content = "📷 Photo")))
        assertNull(MessageTimeline.mediaCaption(message("A", day1, 9, type = MessageType.AUDIO, content = "🎤 Voice message")))
        assertEquals(
            "Class trip",
            MessageTimeline.mediaCaption(message("A", day1, 9, type = MessageType.IMAGE, content = "📷 Class trip"))
        )
        assertNull(MessageTimeline.mediaCaption(message("A", day1, 9, content = "Hello")))
    }
}
