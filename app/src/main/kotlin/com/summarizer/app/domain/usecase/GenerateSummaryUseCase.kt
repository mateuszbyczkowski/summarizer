package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.ParticipantHighlight
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.domain.repository.ThreadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private val modelRepository: ModelRepository
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Generate a summary for the specified thread.
     *
     * @param threadId ID of the thread to summarize
     * @return Result containing the generated Summary or error
     */
    suspend fun execute(threadId: String): Result<Summary> = withContext(Dispatchers.Default) {
        runCatching {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting summary generation for thread: $threadId")

            // Step 1: Ensure model is loaded
            if (!aiEngine.isModelLoaded()) {
                val downloadedModel = modelRepository.getDownloadedModel()
                    ?: throw AIEngineError.ModelNotLoaded

                if (downloadedModel.localFilePath == null) {
                    throw IllegalStateException("Downloaded model has no local file path")
                }

                Timber.i("Loading model: ${downloadedModel.name}")
                aiEngine.loadModel(downloadedModel.localFilePath).getOrThrow()
            }

            // Step 2: Fetch thread and messages
            val thread = threadRepository.getThread(threadId)
                ?: throw IllegalArgumentException("Thread not found: $threadId")

            val messages = messageRepository.getMessagesForThread(threadId).first()

            if (messages.isEmpty()) {
                throw IllegalStateException("No messages found for thread: $threadId")
            }

            Timber.d("Loaded ${messages.size} messages for summarization")

            // Step 3: Build prompt (simple text format, no JSON)
            val prompt = PromptTemplate.buildSummarizationPrompt(
                messages = messages,
                threadName = thread.threadName,
                useJsonOutput = false
            )

            Timber.d("Generated prompt with ${prompt.length} characters")

            // Step 4: Generate summary using AI (simple text generation)
            val rawResponse = aiEngine.generate(
                prompt = prompt,
                systemPrompt = "You are a helpful assistant that summarizes conversations clearly and concisely. Focus on specific details.",
                maxTokens = 512,
                temperature = 0.3f  // Lower temperature for more focused output
            ).getOrThrow()

            Timber.d("Received AI response (${rawResponse.length} chars)")
            Timber.d("Full AI response: $rawResponse")

            // Step 5: Parse JSON response
            val parsedSummary = parseAIResponse(rawResponse)
            Timber.d("Parsed summary - Overview: ${parsedSummary.overview.take(100)}, Key topics: ${parsedSummary.keyTopics}")

            // Step 6: Create Summary domain model
            val summary = Summary(
                threadId = threadId,
                threadName = thread.threadName,
                keyTopics = parsedSummary.keyTopics,
                actionItems = parsedSummary.actionItems.map {
                    ActionItem(
                        task = it.task,
                        assignedTo = it.assignedTo,
                        priority = it.priority ?: "medium"
                    )
                },
                announcements = parsedSummary.announcements,
                participantHighlights = parsedSummary.participantHighlights.map {
                    ParticipantHighlight(
                        participant = it.participant,
                        contribution = it.contribution
                    )
                },
                messageCount = messages.size,
                startTimestamp = messages.minOf { it.timestamp },
                endTimestamp = messages.maxOf { it.timestamp },
                generatedAt = System.currentTimeMillis(),
                rawAIResponse = rawResponse  // Save raw AI output for debugging
            )

            // Step 7: Save to database
            val summaryId = summaryRepository.saveSummary(summary)
            val executionTime = System.currentTimeMillis() - startTime
            Timber.i("Summary saved with ID: $summaryId (execution time: ${executionTime}ms)")

            summary.copy(id = summaryId)
        }
    }

    /**
     * Parse AI-generated text response into structured summary.
     */
    private fun parseAIResponse(rawResponse: String): ParsedSummary {
        Timber.d("Parsing plain text response: $rawResponse")

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
                announcements = emptyList(),
                participantHighlights = emptyList()
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
            .filter { it.isNotBlank() && it.length > 3 }

        return ParsedSummary(
            overview = overview.takeIf { it.isNotBlank() } ?: "Discussion in progress",
            keyTopics = topics.ifEmpty {
                // If still no topics after lenient parsing, try to extract from overview
                extractKeyTopicsFromText(rawResponse).take(3)
            },
            actionItems = actions,
            announcements = announcements,
            participantHighlights = emptyList() // Skip for now to keep it simple
        )
    }

    /**
     * Extract a section from the response text
     */
    private fun extractSection(text: String, sectionHeader: String): String {
        val lines = text.lines()
        val sectionStartIndex = lines.indexOfFirst { it.contains(sectionHeader, ignoreCase = true) }

        if (sectionStartIndex == -1) return ""

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

        return lines.subList(sectionStartIndex + 1, endIndex)
            .joinToString("\n")
            .trim()
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
     * Fallback: extract key topics from unstructured text by looking for keywords.
     */
    private fun extractKeyTopicsFromText(text: String): List<String> {
        // Look for common topic indicators in the text
        val topicKeywords = listOf(
            "chicken dinner", "dinner", "food", "meal",
            "meeting", "event", "plan", "schedule",
            "tomorrow", "today", "date", "time"
        )

        val foundTopics = mutableListOf<String>()
        val lowerText = text.lowercase()

        // Find mentioned topics
        for (keyword in topicKeywords) {
            if (lowerText.contains(keyword) && foundTopics.size < 5) {
                foundTopics.add(keyword.replaceFirstChar { it.uppercase() })
            }
        }

        // If we found some topics, return them
        if (foundTopics.isNotEmpty()) {
            return foundTopics.distinct()
        }

        // Last resort: extract any capitalized phrases or quoted text
        val phrases = text.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 5 && it.length < 50 }
            .take(3)

        return phrases.ifEmpty { listOf("General discussion") }
    }

    /**
     * Data class for parsing AI JSON response.
     */
    @Serializable
    private data class ParsedSummary(
        val overview: String,
        val keyTopics: List<String>,
        val actionItems: List<ParsedActionItem> = emptyList(),
        val announcements: List<String> = emptyList(),
        val participantHighlights: List<ParsedParticipantHighlight> = emptyList()
    )

    @Serializable
    private data class ParsedActionItem(
        val task: String,
        val assignedTo: String? = null,
        val priority: String? = null
    )

    @Serializable
    private data class ParsedParticipantHighlight(
        val participant: String,
        val contribution: String
    )
}
