package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType

/**
 * Prompt for chat summaries.
 *
 * Written for small on-device models as much as for OpenAI: short explicit rules, the
 * instructions repeated after the transcript (small models follow what they read last), and a
 * fixed output format that [SummaryResponseParser] can read back.
 */
object PromptTemplate {

    const val SYSTEM_PROMPT =
        "You write short, accurate summaries of WhatsApp chats. You state only facts found in the messages."

    /** Template lines a model may copy verbatim; the parser drops them. */
    internal val FORMAT_HINTS = listOf(
        "1-2 sentences on what was discussed and decided.",
        "short topic",
        "who does what, by when",
        "important news, decisions or changes"
    )

    /**
     * Build a summarization prompt from a list of messages.
     *
     * @param messages List of messages to summarize
     * @param threadName Name of the conversation thread
     * @param maxTranscriptChars Transcript budget; the oldest messages are dropped beyond it
     * @return Formatted prompt string, or null if there are no summarizable messages
     */
    fun buildSummarizationPrompt(
        messages: List<Message>,
        threadName: String,
        maxTranscriptChars: Int
    ): String? {
        // Deleted messages and group events carry no content worth summarizing
        val validMessages = messages.filter {
            !it.isDeleted && it.messageType != MessageType.DELETED && it.messageType != MessageType.SYSTEM
        }
        if (validMessages.isEmpty()) return null

        val transcript = buildConversationTranscript(validMessages, maxTranscriptChars)

        return """
            |Chat: "$threadName"
            |
            |Messages (oldest first):
            |$transcript
            |
            |Summarize the chat above for someone who did not read it.
            |
            |Rules:
            |- Use only facts from the messages. Never guess or add details.
            |- Write in the same language as the messages, but keep the labels OVERVIEW, TOPICS, ACTIONS and ANNOUNCEMENTS in English.
            |- Be brief and specific: names, dates, times, places, amounts.
            |- "You" is the phone owner.
            |- Use "-" bullets, at most 5 per section. Write "None" if a section is empty.
            |
            |Reply with exactly these four sections and nothing else:
            |OVERVIEW: ${FORMAT_HINTS[0]}
            |TOPICS:
            |- ${FORMAT_HINTS[1]}
            |ACTIONS:
            |- ${FORMAT_HINTS[2]}
            |ANNOUNCEMENTS:
            |- ${FORMAT_HINTS[3]}
        """.trimMargin()
    }

    /**
     * Build conversation transcript from messages, keeping the newest messages when the
     * transcript exceeds [maxChars].
     */
    private fun buildConversationTranscript(messages: List<Message>, maxChars: Int): String {
        val lines = messages.sortedBy { it.timestamp }.map { message ->
            val timestamp = formatMessageTime(message.timestamp)
            val content = when (message.messageType) {
                MessageType.TEXT -> message.content
                MessageType.IMAGE -> "[Photo]"
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
            if (length + line.length + 1 > maxChars) break
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
        val formatter = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}
