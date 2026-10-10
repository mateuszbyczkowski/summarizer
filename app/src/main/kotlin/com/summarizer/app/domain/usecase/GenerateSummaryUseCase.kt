package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.ai.LanguageDetector
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.ai.SummaryResponseParser
import com.summarizer.app.domain.ai.ensureModelLoaded
import com.summarizer.app.domain.model.MessageType
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
    private val modelRepository: ModelRepository,
    private val languageDetector: LanguageDetector
) {

    /**
     * Generate a summary for the specified thread.
     *
     * @param threadId ID of the thread to summarize
     * @param allowRepeat When the thread has no new messages (incremental mode), summarize the
     *        recent conversation again instead of failing. Used for summaries the user asks for;
     *        scheduled runs skip idle threads.
     * @return Result containing the generated Summary, or [NothingToSummarizeException] when the
     *         thread has no (new) messages to summarize
     */
    suspend fun execute(threadId: String, allowRepeat: Boolean = false): Result<Summary> = withContext(Dispatchers.Default) {
        suspendRunCatching {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting summary generation for thread: $threadId")

            // Step 1: Fetch thread and its settings
            val thread = threadRepository.getThread(threadId)
                ?: throw IllegalArgumentException("Thread not found: $threadId")
            val settings = threadSettingsRepository.getOrCreateSettings(threadId)

            // Step 2: Fetch messages based on summarization mode
            val lastTimestamp = settings.lastSummarizedMessageTimestamp
            val incremental = settings.summarizationMode == SummarizationMode.INCREMENTAL && lastTimestamp != null
            val newMessages = if (incremental) {
                messageRepository.getMessagesForThreadSince(threadId, lastTimestamp + 1)
            } else {
                emptyList()
            }
            // Full history (the prompt keeps the newest messages that fit) when not incremental,
            // or when the user asks again although nothing new arrived
            val messages = if (incremental && (newMessages.isNotEmpty() || !allowRepeat)) {
                newMessages
            } else {
                messageRepository.getMessagesForThread(threadId).first()
            }

            // Step 3: Build prompt (before loading the model, so idle threads stay cheap)
            val language = languageDetector.detectLanguageName(
                PromptTemplate.summarizableMessages(messages)
                    .filter { it.messageType == MessageType.TEXT }
                    .joinToString("\n") { it.content }
            )
            val prompt = PromptTemplate.buildSummarizationPrompt(
                messages = messages,
                threadName = thread.threadName,
                maxTranscriptChars = aiEngine.maxPromptChars(),
                languageName = language
            ) ?: throw NothingToSummarizeException(
                if (incremental && !allowRepeat) "No new messages since the last summary" else "No messages to summarize"
            )

            Timber.d("Summarizing ${messages.size} messages (mode: ${settings.summarizationMode}, language: $language, prompt: ${prompt.length} chars)")

            // Step 4: Ensure the active engine is ready
            aiEngine.ensureModelLoaded(modelRepository).getOrElse { error ->
                throw IllegalStateException("Failed to load AI model: ${error.message}", error)
            }

            // Step 5: Generate summary. Transient failures are retried by the engine itself.
            val rawResponse = aiEngine.generate(
                prompt = prompt,
                systemPrompt = PromptTemplate.SYSTEM_PROMPT,
                maxTokens = SUMMARY_MAX_TOKENS,
                temperature = 0.2f // Low temperature: factual, consistent wording
            ).getOrElse { error ->
                throw IllegalStateException("Failed to generate summary: ${error.message ?: "AI generation error"}", error)
            }

            // Step 6: Parse response into a Summary domain model
            val parsed = SummaryResponseParser.parse(rawResponse)
            val summary = Summary(
                threadId = threadId,
                threadName = thread.threadName,
                overview = parsed.overview,
                keyTopics = parsed.topics,
                actionItems = parsed.actions.map { ActionItem(task = it, assignedTo = null, priority = "medium") },
                announcements = parsed.announcements,
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

    private companion object {
        // A concise four-section summary fits comfortably; also bounds on-device generation time
        const val SUMMARY_MAX_TOKENS = 400
    }
}

/**
 * Thrown when a thread has no (new) messages to summarize. Not an error for scheduled runs.
 */
class NothingToSummarizeException(message: String) : Exception(message)
