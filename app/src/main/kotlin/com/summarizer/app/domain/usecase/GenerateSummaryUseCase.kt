package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.LanguageDetector
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.ai.SummaryResponseParser
import com.summarizer.app.domain.ai.ensureModelLoaded
import com.summarizer.app.domain.model.GenerationStage
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
import java.time.Instant
import java.time.ZoneId
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
     * @param forceFull Summarize the whole conversation this time, whatever the thread's
     *        summarization mode (a one-off full re-summary)
     * @param onProgress Receives each stage and, while the model writes, the overview written so
     *        far; may be called from a background thread
     * @return Result containing the generated Summary, or [NothingToSummarizeException] when the
     *         thread has no (new) messages to summarize
     */
    suspend fun execute(
        threadId: String,
        allowRepeat: Boolean = false,
        forceFull: Boolean = false,
        onProgress: ((stage: GenerationStage, partialOverview: String?) -> Unit)? = null
    ): Result<Summary> = withContext(Dispatchers.Default) {
        suspendRunCatching {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting summary generation for thread: $threadId")
            onProgress?.invoke(GenerationStage.PREPARING, null)

            // Step 1: Fetch thread and its settings
            val thread = threadRepository.getThread(threadId)
                ?: throw IllegalArgumentException("Thread not found: $threadId")
            val settings = threadSettingsRepository.getOrCreateSettings(threadId)

            // Step 2: Fetch messages based on summarization mode
            val lastTimestamp = settings.lastSummarizedMessageTimestamp
            val incremental = !forceFull && settings.summarizationMode == SummarizationMode.INCREMENTAL && lastTimestamp != null
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

            // Step 3: Skip idle threads before loading the model, which is expensive
            val summarizable = PromptTemplate.summarizableMessages(messages)
            if (summarizable.isEmpty()) {
                throw NothingToSummarizeException(
                    if (incremental && !allowRepeat) "No new messages since the last summary" else "No messages to summarize"
                )
            }

            // Step 4: Ensure the engine is ready; the prompt budget depends on which engine
            // actually loaded (a GPU model may fall back to a CPU model)
            onProgress?.invoke(GenerationStage.LOADING_MODEL, null)
            aiEngine.ensureModelLoaded(modelRepository).getOrElse { error ->
                throw IllegalStateException("Failed to load AI model: ${error.message}", error)
            }
            val language = languageDetector.detectLanguageName(
                summarizable.filter { it.messageType == MessageType.TEXT }.joinToString("\n") { it.content }
            )
            // Built for the engine loaded right now: its prompt budget differs (GPU vs CPU model)
            val buildPrompt = suspend {
                PromptTemplate.buildSummarizationPrompt(
                    messages = summarizable,
                    threadName = thread.threadName,
                    maxTranscriptChars = aiEngine.maxPromptChars(),
                    languageName = language
                ) ?: throw NothingToSummarizeException("No messages to summarize")
            }

            // Step 5: Generate summary. Transient failures are retried by the engine itself.
            onProgress?.invoke(GenerationStage.READING, null)
            val generate = suspend {
                val prompt = buildPrompt()
                Timber.d("Summarizing ${messages.size} messages (mode: ${if (incremental) "incremental" else "full"}, language: $language, prompt: ${prompt.length} chars)")
                aiEngine.generate(
                    prompt = prompt,
                    systemPrompt = PromptTemplate.systemPrompt(language),
                    maxTokens = SUMMARY_MAX_TOKENS,
                    temperature = 0.2f, // Low temperature: factual, consistent wording
                    onPartial = onProgress?.let { report ->
                        { partial -> report(GenerationStage.WRITING, SummaryResponseParser.streamingOverview(partial)) }
                    }
                )
            }
            var generated = generate()
            // The model was swapped while this summary waited for another one (a model chosen in
            // the model manager, or one that just finished downloading): load it and try again
            if (generated.exceptionOrNull() is AIEngineError.ModelNotLoaded &&
                aiEngine.ensureModelLoaded(modelRepository).isSuccess
            ) {
                generated = generate()
            }
            val rawResponse = generated.getOrElse { error ->
                throw IllegalStateException("Failed to generate summary: ${error.message ?: "AI generation error"}", error)
            }

            // Step 6: Parse response into a Summary domain model. Dates without a year are
            // resolved relative to the day of the last summarized message.
            val endTimestamp = messages.maxOf { it.timestamp }
            val lastMessageDay = Instant.ofEpochMilli(endTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            val parsed = SummaryResponseParser.parse(rawResponse, lastMessageDay)
            val summary = Summary(
                threadId = threadId,
                threadName = thread.threadName,
                overview = parsed.overview,
                keyTopics = parsed.topics,
                actionItems = parsed.actions,
                announcements = parsed.announcements,
                participantHighlights = emptyList(),
                messageCount = messages.size,
                startTimestamp = messages.minOf { it.timestamp },
                endTimestamp = endTimestamp,
                generatedAt = System.currentTimeMillis(),
                rawAIResponse = rawResponse,  // Save raw AI output for debugging
                keyDates = parsed.keyDates,
                engine = aiEngine.engineName()
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
        // A concise five-section summary fits comfortably; also bounds on-device generation time
        const val SUMMARY_MAX_TOKENS = 512
    }
}

/**
 * Thrown when a thread has no (new) messages to summarize. Not an error for scheduled runs.
 */
class NothingToSummarizeException(message: String) : Exception(message)
