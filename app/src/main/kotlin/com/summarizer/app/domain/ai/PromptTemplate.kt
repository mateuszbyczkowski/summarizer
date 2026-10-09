package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType

/**
 * Prompt engineering templates for AI summarization tasks.
 */
object PromptTemplate {

    // RealAIEngine runs with a 16K-token context. After the instructions and ~512 generated tokens,
    // ~15K tokens remain for the transcript. Non-English text and emoji often tokenize at 2-3
    // chars/token, so budget conservatively at ~2.4 chars/token.
    private const val MAX_TRANSCRIPT_CHARS = 36_000

    /**
     * Build a summarization prompt from a list of messages.
     *
     * @param messages List of messages to summarize
     * @param threadName Name of the conversation thread
     * @return Formatted prompt string, or null if there are no summarizable messages
     */
    fun buildSummarizationPrompt(
        messages: List<Message>,
        threadName: String
    ): String? {
        // Filter out deleted and system messages
        val validMessages = messages.filter {
            !it.isDeleted && it.messageType != MessageType.DELETED && it.messageType != MessageType.SYSTEM
        }

        if (validMessages.isEmpty()) return null

        val transcript = buildConversationTranscript(validMessages)

        return buildString {
            appendLine("Summarize this WhatsApp conversation from '$threadName':")
            appendLine()
            appendLine(transcript)
            appendLine()
            appendLine("Summary:")
            appendLine("OVERVIEW: (Write 1-2 sentences about what was discussed)")
            appendLine("TOPICS: (List 2-3 main topics)")
            appendLine("ACTIONS: (List any tasks or plans mentioned)")
            appendLine("ANNOUNCEMENTS: (List any important news or announcements)")
            appendLine()
            appendLine("Be specific and mention concrete details like names, dates, places, and plans.")
        }
    }

    /**
     * Build conversation transcript from messages, keeping the newest messages when the
     * transcript exceeds [MAX_TRANSCRIPT_CHARS].
     */
    private fun buildConversationTranscript(messages: List<Message>): String {
        val lines = messages.sortedBy { it.timestamp }.map { message ->
            val timestamp = formatMessageTime(message.timestamp)
            val content = when (message.messageType) {
                MessageType.TEXT -> message.content
                MessageType.IMAGE -> "[Image]"
                MessageType.VIDEO -> "[Video]"
                MessageType.DOCUMENT -> "[Document]"
                MessageType.AUDIO -> "[Audio]"
                MessageType.LOCATION -> "[Location]"
                MessageType.CONTACT -> "[Contact]"
                MessageType.STICKER -> "[Sticker]"
                else -> "[Unknown]"
            }
            "[$timestamp] ${message.sender}: $content"
        }

        val kept = ArrayDeque<String>()
        var length = 0
        for (line in lines.asReversed()) {
            if (length + line.length + 1 > MAX_TRANSCRIPT_CHARS) break
            kept.addFirst(line)
            length += line.length + 1
        }
        val omitted = lines.size - kept.size
        return buildString {
            if (omitted > 0) appendLine("[$omitted earlier messages omitted due to length]")
            append(kept.joinToString("\n"))
        }
    }

    /**
     * Format timestamp for message display.
     */
    private fun formatMessageTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}
