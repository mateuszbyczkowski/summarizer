package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.ai.ensureModelLoaded
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for generating AI-powered summaries of WhatsApp thread conversations.
 *
 * Orchestrates the complete summarization workflow:
 * 1. Load AI model (if not already loaded)
 * 2. Fetch messages from thread
 * 3. Format prompt with message content
 * 4. Generate summary using AI
 * 5. Parse and validate response
 * 6. Save summary to database
 */
class GenerateSummaryUseCase @Inject constructor(
    private val aiEngine: AIEngine,
    private val messageRepository: MessageRepository,
    private val summaryRepository: SummaryRepository,
    private val threadRepository: ThreadRepository,
    private val threadSettingsRepository: ThreadSettingsRepository,
    private val modelRepository: ModelRepository
) {

    /**
     * Generate a summary for the specified thread.
     *
     * @param threadId ID of the thread to summarize
     * @return Result containing the generated Summary, or [NothingToSummarizeException] when the
     *         thread has no (new) messages to summarize
     */
    suspend fun execute(threadId: String): Result<Summary> = withContext(Dispatchers.Default) {
        suspendRunCatching {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting summary generation for thread: $threadId")

            // Step 1: Ensure the active engine is ready
            aiEngine.ensureModelLoaded(modelRepository).getOrElse { error ->
                throw IllegalStateException("Failed to load AI model: ${error.message}", error)
            }

            // Step 2: Fetch thread and its settings
            val thread = threadRepository.getThread(threadId)
                ?: throw IllegalArgumentException("Thread not found: $threadId")
            val settings = threadSettingsRepository.getOrCreateSettings(threadId)

            // Step 3: Fetch messages based on summarization mode
            val lastTimestamp = settings.lastSummarizedMessageTimestamp
            val messages = if (settings.summarizationMode == SummarizationMode.INCREMENTAL && lastTimestamp != null) {
                messageRepository.getMessagesForThreadSince(threadId, lastTimestamp + 1)
            } else {
                messageRepository.getMessagesForThread(threadId).first()
            }

            // Step 4: Build prompt
            val prompt = PromptTemplate.buildSummarizationPrompt(
                messages = messages,
                threadName = thread.threadName
            ) ?: throw NothingToSummarizeException(
                if (settings.summarizationMode == SummarizationMode.INCREMENTAL && lastTimestamp != null) {
                    "No new messages since the last summary"
                } else {
                    "No messages to summarize"
                }
            )

            Timber.d("Summarizing ${messages.size} messages (mode: ${settings.summarizationMode}, prompt: ${prompt.length} chars)")

            // Step 5: Generate summary. Transient failures are retried by the engine itself.
            val rawResponse = aiEngine.generate(
                prompt = prompt,
                systemPrompt = "You are a helpful assistant that summarizes conversations clearly and concisely. Focus on specific details.",
                maxTokens = 512,
                temperature = 0.3f  // Lower temperature for more focused output
            ).getOrElse { error ->
                throw IllegalStateException("Failed to generate summary: ${error.message ?: "AI generation error"}", error)
            }

            // Step 6: Parse response into a Summary domain model
            val parsedSummary = parseAIResponse(rawResponse)
            val summary = Summary(
                threadId = threadId,
                threadName = thread.threadName,
                overview = parsedSummary.overview,
                keyTopics = parsedSummary.keyTopics,
                actionItems = parsedSummary.actionItems.map {
                    ActionItem(
                        task = it.task,
                        assignedTo = it.assignedTo,
                        priority = it.priority ?: "medium"
                    )
                },
                announcements = parsedSummary.announcements,
                participantHighlights = emptyList(),
                messageCount = messages.size,
                startTimestamp = messages.minOf { it.timestamp },
                endTimestamp = messages.maxOf { it.timestamp },
                generatedAt = System.currentTimeMillis(),
                rawAIResponse = rawResponse  // Save raw AI output for debugging
            )

            // Step 7: Save and record what was summarized
            val summaryId = summaryRepository.saveSummary(summary)
            threadSettingsRepository.updateLastSummarized(
                threadId = threadId,
                messageTimestamp = summary.endTimestamp,
                summarizedAt = summary.generatedAt
            )

            Timber.i("Summary saved with ID: $summaryId (execution time: ${System.currentTimeMillis() - startTime}ms)")
            summary.copy(id = summaryId)
        }
    }

    /**
     * Parse AI-generated text response into structured summary.
     */
    private fun parseAIResponse(rawResponse: String): ParsedSummary {
        // Try to extract sections with headers first
        val overview = extractSection(rawResponse, "OVERVIEW:")
        val topicsText = extractSection(rawResponse, "TOPICS:")
        val actionsText = extractSection(rawResponse, "ACTIONS:")
        val announcementsText = extractSection(rawResponse, "ANNOUNCEMENTS:")

        // Check if we found any structured sections
        val hasStructuredFormat = overview.isNotBlank() || topicsText.isNotBlank() ||
                                  actionsText.isNotBlank() || announcementsText.isNotBlank()

        if (!hasStructuredFormat) {
            // Model didn't follow format - extract from unstructured text
            Timber.w("Model didn't follow section format, extracting from unstructured text")
            return ParsedSummary(
                overview = extractOverview(rawResponse),
                keyTopics = extractKeyTopicsFromText(rawResponse),
                actionItems = emptyList(),
                announcements = emptyList()
            )
        }

        // Parse topics from text - be more lenient with extraction
        val topics = topicsText
            .split("\n")  // Only split by newlines, not commas (topics may contain commas)
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").removePrefix("1.").removePrefix("2.").removePrefix("3.").removePrefix("4.").removePrefix("5.").trim() }
            .filter { it.isNotBlank() && it.length > 1 }  // More lenient - accept even short topics
            .take(5)

        // Parse action items
        val actions = actionsText
            .split("\n", ";")
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { it.isNotBlank() && it.length > 3 }
            .map { ParsedActionItem(task = it) }

        // Parse announcements
        val announcements = announcementsText
            .split("\n", ";")
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { announcement ->
                announcement.isNotBlank() &&
                announcement.length > 3 &&
                // Filter out placeholder/instruction text
                !announcement.startsWith("List any", ignoreCase = true) &&
                !announcement.startsWith("(List", ignoreCase = true) &&
                !announcement.contains("important news or announcements", ignoreCase = true)
            }

        return ParsedSummary(
            overview = overview.takeIf { it.isNotBlank() } ?: "Discussion in progress",
            keyTopics = topics.ifEmpty {
                // If still no topics after lenient parsing, try to extract from overview
                extractKeyTopicsFromText(rawResponse).take(3)
            },
            actionItems = actions,
            announcements = announcements
        )
    }

    /**
     * Extract a section from the response text
     */
    private fun extractSection(text: String, sectionHeader: String): String {
        val lines = text.lines()
        val sectionStartIndex = lines.indexOfFirst { it.contains(sectionHeader, ignoreCase = true) }

        if (sectionStartIndex == -1) return ""

        // Check if there's content on the same line as the header (after the colon)
        val headerLine = lines[sectionStartIndex]
        val headerContent = headerLine.substringAfter(sectionHeader, "")
            .removePrefix("(").removeSuffix(")")  // Remove placeholder text in parentheses
            .trim()

        // Find the next section header or end of text
        val nextSectionIndex = lines.drop(sectionStartIndex + 1).indexOfFirst { line ->
            line.contains("OVERVIEW:", ignoreCase = true) ||
            line.contains("TOPICS:", ignoreCase = true) ||
            line.contains("ACTIONS:", ignoreCase = true) ||
            line.contains("ANNOUNCEMENTS:", ignoreCase = true)
        }

        val endIndex = if (nextSectionIndex == -1) {
            lines.size
        } else {
            sectionStartIndex + 1 + nextSectionIndex
        }

        // Combine header line content (if any) with subsequent lines
        val subsequentLines = lines.subList(sectionStartIndex + 1, endIndex)
            .joinToString("\n")
            .trim()

        return if (headerContent.isNotBlank() && !headerContent.startsWith("(")) {
            // Content exists on header line and it's not just placeholder text
            if (subsequentLines.isNotBlank()) {
                "$headerContent\n$subsequentLines"
            } else {
                headerContent
            }
        } else {
            subsequentLines
        }
    }

    /**
     * Fallback: extract overview from unstructured text.
     */
    private fun extractOverview(text: String): String {
        val trimmed = text.trim()

        // If the text is reasonably short, use it as-is
        if (trimmed.length <= 300) {
            return trimmed
        }

        // Otherwise, take first few sentences
        val sentences = trimmed.split(". ")
        val overview = sentences.take(2).joinToString(". ")
        return if (overview.endsWith(".")) overview else "$overview."
    }

    /**
     * Fallback: use short phrases from unstructured text as topics.
     */
    private fun extractKeyTopicsFromText(text: String): List<String> {
        val phrases = text.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.length in 6..49 }
            .take(3)

        return phrases.ifEmpty { listOf("General discussion") }
    }

    private data class ParsedSummary(
        val overview: String,
        val keyTopics: List<String>,
        val actionItems: List<ParsedActionItem> = emptyList(),
        val announcements: List<String> = emptyList()
    )

    private data class ParsedActionItem(
        val task: String,
        val assignedTo: String? = null,
        val priority: String? = null
    )
}

/**
 * Thrown when a thread has no (new) messages to summarize. Not an error for scheduled runs.
 */
class NothingToSummarizeException(message: String) : Exception(message)
