package com.summarizer.app.service

import com.summarizer.app.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppNotificationParserTest {

    private fun snapshot(
        title: String? = "Family",
        text: String? = null,
        conversationTitle: String? = null,
        isGroupConversation: Boolean = false,
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false,
        category: String? = "msg",
        messages: List<SnapshotMessage> = emptyList()
    ) = NotificationSnapshot(
        title = title,
        text = text,
        bigText = null,
        conversationTitle = conversationTitle,
        isGroupConversation = isGroupConversation,
        isGroupSummary = isGroupSummary,
        isOngoing = isOngoing,
        category = category,
        timestamp = 1_000L,
        messages = messages
    )

    @Test
    fun `messaging style returns every message with its own sender and time`() {
        val parsed = WhatsAppNotificationParser.parse(
            snapshot(
                conversationTitle = "Family (3 messages)",
                isGroupConversation = true,
                messages = listOf(
                    SnapshotMessage("Anna", "Dinner at 7?", 100L),
                    SnapshotMessage("Piotr", "Meet at 10:30", 200L),
                    SnapshotMessage(null, "Sure", 300L)
                )
            )
        )

        assertEquals(3, parsed.size)
        assertEquals(listOf("Anna", "Piotr", "You"), parsed.map { it.sender })
        assertEquals("Meet at 10:30", parsed[1].content)
        assertEquals(listOf(100L, 200L, 300L), parsed.map { it.timestamp })
        assertTrue(parsed.all { it.threadName == "Family" })
    }

    @Test
    fun `one to one chat uses the contact as thread and sender`() {
        val parsed = WhatsAppNotificationParser.parse(
            snapshot(
                title = "Anna",
                messages = listOf(SnapshotMessage("Anna", "Re: dinner", 100L))
            )
        )

        assertEquals("Anna", parsed.single().threadName)
        assertEquals("Anna", parsed.single().sender)
        assertEquals("Re: dinner", parsed.single().content)
    }

    @Test
    fun `plain text is never split on colons`() {
        val parsed = WhatsAppNotificationParser.parse(snapshot(title = "Anna", text = "Meet at 10:30"))

        assertEquals("Anna", parsed.single().sender)
        assertEquals("Meet at 10:30", parsed.single().content)
    }

    @Test
    fun `summary, ongoing and non-message notifications are skipped`() {
        assertTrue(WhatsAppNotificationParser.parse(snapshot(text = "5 new messages")).isEmpty())
        assertTrue(WhatsAppNotificationParser.parse(snapshot(text = "5 messages from 3 chats")).isEmpty())
        assertTrue(WhatsAppNotificationParser.parse(snapshot(text = "hi", isGroupSummary = true)).isEmpty())
        assertTrue(WhatsAppNotificationParser.parse(snapshot(text = "Checking for new messages", isOngoing = true)).isEmpty())
        assertTrue(WhatsAppNotificationParser.parse(snapshot(text = "Incoming voice call", category = "call")).isEmpty())
    }

    @Test
    fun `ordinary sentences are not classified as system messages`() {
        listOf("I left the keys at reception", "added the slides", "leftovers anyone?").forEach {
            assertEquals(it, MessageType.TEXT, WhatsAppNotificationParser.detectMessageType(it))
        }
    }

    @Test
    fun `group events, deleted messages and media are detected`() {
        assertEquals(MessageType.SYSTEM, WhatsAppNotificationParser.detectMessageType("Anna changed the subject to \"Trip\""))
        assertEquals(MessageType.SYSTEM, WhatsAppNotificationParser.detectMessageType("You're now an admin"))
        assertEquals(MessageType.DELETED, WhatsAppNotificationParser.detectMessageType("This message was deleted"))
        assertEquals(MessageType.IMAGE, WhatsAppNotificationParser.detectMessageType("📷 Photo"))
        assertEquals(MessageType.STICKER, WhatsAppNotificationParser.detectMessageType("sticker omitted"))
    }
}
