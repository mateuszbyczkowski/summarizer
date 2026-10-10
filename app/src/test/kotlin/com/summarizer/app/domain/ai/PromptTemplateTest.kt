package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {

    private fun message(i: Int, content: String = "message number $i", type: MessageType = MessageType.TEXT) = Message(
        threadId = "t",
        threadName = "Thread",
        sender = "Sender",
        content = content,
        timestamp = i * 60_000L,
        messageType = type
    )

    @Test
    fun `the chat description comes before the messages`() {
        val prompt = PromptTemplate.buildSummarizationPrompt(
            listOf(message(1)), "Klasa 3B", maxTranscriptChars = 8_500, chatDescription = "Parents of class 3B"
        )!!
        val about = prompt.indexOf("About this chat (from the owner): Parents of class 3B")
        assertTrue(about > prompt.indexOf("Chat: \"Klasa 3B\""))
        assertTrue(about < prompt.indexOf(message(1).content))
    }

    @Test
    fun `no description adds no line`() {
        val prompt = PromptTemplate.buildSummarizationPrompt(listOf(message(1)), "Thread", maxTranscriptChars = 8_500)!!
        assertFalse(prompt.contains("About this chat"))
    }

    @Test
    fun `long transcripts keep the newest messages`() {
        val messages = (1..2_000).map { message(it, "message number $it " + "x".repeat(40)) }

        val prompt = PromptTemplate.buildSummarizationPrompt(messages, "Thread", maxTranscriptChars = 8_500)!!

        assertTrue(prompt.contains("message number 2000 "))
        assertFalse(prompt.contains("message number 1 "))
        assertTrue(prompt.contains("earlier messages omitted"))
    }

    @Test
    fun `only system and deleted messages produce no prompt`() {
        val messages = listOf(
            message(1, "Anna created group \"Trip\"", MessageType.SYSTEM),
            message(2, "This message was deleted", MessageType.DELETED)
        )

        assertNull(PromptTemplate.buildSummarizationPrompt(messages, "Thread", maxTranscriptChars = 8_500))
    }

    @Test
    fun `dates are limited to planned events with the start time first`() {
        val prompt = PromptTemplate.buildSummarizationPrompt(listOf(message(1)), "Thread", maxTranscriptChars = 8_500)!!

        assertTrue(prompt.contains("DATES are only planned events and deadlines"))
        assertTrue(prompt.contains("never when a message was sent"))
        assertTrue(prompt.contains("HH:MM-HH:MM, start first"))
        // The labels stay on lines of their own, as the parser expects
        assertTrue(prompt.lines().contains("DATES:"))
    }
}
