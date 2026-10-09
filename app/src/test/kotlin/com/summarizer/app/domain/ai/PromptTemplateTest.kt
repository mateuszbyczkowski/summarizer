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
    fun `long transcripts keep the newest messages`() {
        val messages = (1..2_000).map { message(it, "message number $it " + "x".repeat(40)) }

        val prompt = PromptTemplate.buildSummarizationPrompt(messages, "Thread")!!

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

        assertNull(PromptTemplate.buildSummarizationPrompt(messages, "Thread"))
    }
}
