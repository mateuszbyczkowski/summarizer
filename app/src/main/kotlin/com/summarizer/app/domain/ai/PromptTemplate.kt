package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.Message

/**
 * Prompt engineering templates for AI summarization tasks.
 */
object PromptTemplate {

    /**
     * System prompt that defines the AI's role and behavior for summarization.
     */
    const val SUMMARIZATION_SYSTEM_PROMPT = """You are an AI assistant specialized in summarizing WhatsApp group conversations. Your task is to analyze chat messages and provide concise, structured summaries.

Guidelines:
- Focus on key discussion topics and important information
- Identify action items, announcements, and decisions
- Highlight notable participant contributions
- Exclude deleted messages and system notifications
- Be concise and objective
- Output valid JSON following the specified schema"""

    /**
     * JSON schema for structured summary output.
     */
    const val SUMMARY_JSON_SCHEMA = """{
  "type": "object",
  "properties": {
    "overview": {
      "type": "string",
      "description": "2-3 sentence summary of the conversation"
    },
    "keyTopics": {
      "type": "array",
      "items": {"type": "string"},
      "description": "List of main discussion topics (3-5 items)"
    },
    "actionItems": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "task": {"type": "string"},
          "assignedTo": {"type": "string"},
          "priority": {"type": "string", "enum": ["high", "medium", "low"]}
        },
        "required": ["task"]
      },
      "description": "Action items or tasks mentioned"
    },
    "announcements": {
      "type": "array",
      "items": {"type": "string"},
      "description": "Important announcements or decisions"
    },
    "participantHighlights": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "participant": {"type": "string"},
          "contribution": {"type": "string"}
        },
        "required": ["participant", "contribution"]
      },
      "description": "Notable contributions from specific participants"
    }
  },
  "required": ["overview", "keyTopics"]
}"""

    /**
     * Build a summarization prompt from a list of messages.
     *
     * @param messages List of messages to summarize
     * @param threadName Name of the conversation thread
     * @param useJsonOutput Whether to request JSON-formatted output
     * @return Formatted prompt string
     */
    fun buildSummarizationPrompt(
        messages: List<Message>,
        threadName: String,
        useJsonOutput: Boolean = true
    ): String {
        // Filter out deleted and system messages
        val validMessages = messages.filter {
            !it.isDeleted && it.messageType != com.summarizer.app.domain.model.MessageType.DELETED &&
                    it.messageType != com.summarizer.app.domain.model.MessageType.SYSTEM
        }

        if (validMessages.isEmpty()) {
            return "No valid messages to summarize."
        }

        val messageCount = validMessages.size
        val participantCount = validMessages.map { it.sender }.distinct().size
        val timeRange = formatTimeRange(
            validMessages.minOf { it.timestamp },
            validMessages.maxOf { it.timestamp }
        )

        // Build conversation transcript
        val transcript = buildConversationTranscript(validMessages)

        // Truncate if necessary to fit context window (conservative limit: ~1500 tokens = ~6000 chars)
        val truncatedTranscript = if (transcript.length > 5000) {
            "${transcript.take(5000)}...\n[Transcript truncated due to length]"
        } else {
            transcript
        }

        return buildString {
            appendLine("Summarize this WhatsApp conversation from '$threadName':")
            appendLine()
            appendLine(truncatedTranscript)
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
     * Build conversation transcript from messages.
     */
    private fun buildConversationTranscript(messages: List<Message>): String {
        return messages.joinToString("\n") { message ->
            val timestamp = formatMessageTime(message.timestamp)
            val content = when (message.messageType) {
                com.summarizer.app.domain.model.MessageType.TEXT -> message.content
                com.summarizer.app.domain.model.MessageType.IMAGE -> "[Image]"
                com.summarizer.app.domain.model.MessageType.VIDEO -> "[Video]"
                com.summarizer.app.domain.model.MessageType.DOCUMENT -> "[Document]"
                com.summarizer.app.domain.model.MessageType.AUDIO -> "[Audio]"
                com.summarizer.app.domain.model.MessageType.LOCATION -> "[Location]"
                com.summarizer.app.domain.model.MessageType.CONTACT -> "[Contact]"
                com.summarizer.app.domain.model.MessageType.STICKER -> "[Sticker]"
                else -> "[Unknown]"
            }
            "[$timestamp] ${message.sender}: $content"
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

    /**
     * Format time range for conversation metadata.
     */
    private fun formatTimeRange(startTime: Long, endTime: Long): String {
        val start = java.util.Date(startTime)
        val end = java.util.Date(endTime)
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())

        val startStr = formatter.format(start)
        val endStr = formatter.format(end)

        return if (isSameDay(start, end)) {
            val dateFormatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            "${dateFormatter.format(start)} ${timeFormatter.format(start)} - ${timeFormatter.format(end)}"
        } else {
            "$startStr - $endStr"
        }
    }

    /**
     * Check if two dates are on the same day.
     */
    private fun isSameDay(date1: java.util.Date, date2: java.util.Date): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
        val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
