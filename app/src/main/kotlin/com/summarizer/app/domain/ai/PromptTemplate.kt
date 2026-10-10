package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType

/**
 * Prompt for chat summaries.
 *
 * Kept short on purpose: on-device models read prompts slowly (every instruction costs
 * seconds) and follow few, concrete rules best. The instructions come after the transcript
 * (small models follow what they read last), name the output language explicitly, and fix a
 * format that [SummaryResponseParser] reads back.
 */
object PromptTemplate {

    const val SYSTEM_PROMPT =
        "You write short, accurate summaries of WhatsApp chats. You state only facts found in the messages."

    /**
     * System prompt naming the output language; models drift back to English after the English
     * section labels unless the language is restated here.
     */
    fun systemPrompt(languageName: String?): String =
        if (languageName == null) SYSTEM_PROMPT
        else "$SYSTEM_PROMPT Always write in $languageName; only the five section labels stay in English."

    /** Template lines a model may copy verbatim; the parser drops them. */
    internal val FORMAT_HINTS = listOf(
        "<1-2 sentences: what happened and what was decided>",
        "<topic in a few words>",
        "<task someone must do and who> (by DD.MM)",
        "<important news or change>",
        "DD.MM HH:MM | <event, place>"
    )

    /**
     * Build a summarization prompt from a list of messages.
     *
     * @param messages List of messages to summarize
     * @param threadName Name of the conversation thread
     * @param maxTranscriptChars Transcript budget; the oldest messages are dropped beyond it
     * @param languageName Language to write in (e.g. "Polish"); null keeps the chat's language
     * @return Formatted prompt string, or null if there are no summarizable messages
     */
    fun buildSummarizationPrompt(
        messages: List<Message>,
        threadName: String,
        maxTranscriptChars: Int,
        languageName: String? = null
    ): String? {
        val validMessages = summarizableMessages(messages)
        if (validMessages.isEmpty()) return null

        val transcript = buildConversationTranscript(validMessages, maxTranscriptChars)
        val language = languageName ?: "the language of the messages"

        return """
            |Chat: "$threadName"
            |
            |$transcript
            |
            |Summarize this chat in $language. Use only facts from the messages; do not guess. If something changed later in the chat, give only the latest information. Be specific (names, dates, times, places, amounts) and keep each bullet under 15 words. "You" is the phone owner.
            |
            |Reply in exactly this format, keeping the five labels in English, with at most 3 bullets per section and "None" if a section is empty. Write dates as DD.MM and times as HH:MM:
            |OVERVIEW: ${FORMAT_HINTS[0]}
            |TOPICS:
            |- ${FORMAT_HINTS[1]}
            |ACTIONS:
            |- ${FORMAT_HINTS[2]}
            |ANNOUNCEMENTS:
            |- ${FORMAT_HINTS[3]}
            |DATES:
            |- ${FORMAT_HINTS[4]}
        """.trimMargin()
    }

    /** Deleted messages and group events carry no content worth summarizing. */
    fun summarizableMessages(messages: List<Message>): List<Message> =
        messages.filter {
            !it.isDeleted && it.messageType != MessageType.DELETED && it.messageType != MessageType.SYSTEM
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
        val formatter = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.ENGLISH)
        return formatter.format(date)
    }
}
