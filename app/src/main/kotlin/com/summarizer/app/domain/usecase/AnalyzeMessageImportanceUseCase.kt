package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.ensureModelLoaded
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for analyzing message importance using AI.
 *
 * Determines if a message is "important" enough to trigger a notification
 * based on content analysis.
 */
@Singleton
class AnalyzeMessageImportanceUseCase @Inject constructor(
    private val aiEngine: AIEngine,
    private val modelRepository: ModelRepository,
    private val preferencesRepository: PreferencesRepository
) {

    /** Only one AI analysis at a time; bursts of messages fall back instead of queueing up. */
    private val aiAnalysisLock = Mutex()

    /**
     * Analyze a message and return an importance score (0.0 - 1.0).
     *
     * Scores:
     * - 0.0-0.3: Low importance (casual chat, reactions, etc.)
     * - 0.3-0.6: Medium importance (regular conversation)
     * - 0.6-0.8: High importance (questions, decisions, time-sensitive)
     * - 0.8-1.0: Critical importance (urgent, action required)
     *
     * AI analysis only runs on-device: individual messages are never sent to a cloud provider.
     *
     * @param messageContent The message text to analyze
     * @param senderName The name of the sender
     * @return Importance score between 0.0 and 1.0
     */
    suspend fun execute(messageContent: String, senderName: String): Float {
        analyzeWithHeuristics(messageContent)?.let { return it }

        if (preferencesRepository.getAIProvider() != AIProvider.LOCAL || !aiAnalysisLock.tryLock()) {
            return FALLBACK_SCORE
        }
        return try {
            analyzeWithAI(messageContent, senderName)
        } finally {
            aiAnalysisLock.unlock()
        }
    }

    /**
     * Check if a score meets the user's smart notification threshold.
     */
    suspend fun meetsThreshold(score: Float): Boolean =
        score >= preferencesRepository.getSmartNotificationThreshold()

    /**
     * Quick heuristic-based importance analysis.
     * Returns a score if the message matches obvious patterns, null otherwise.
     */
    private fun analyzeWithHeuristics(content: String): Float? {
        val lowerContent = content.lowercase()

        // Very low importance: single emoji, reactions, very short messages
        if (content.length <= 3 ||
            content.matches(Regex("^[😀-🙏🌀-🗿]+$")) ||
            lowerContent in listOf("ok", "k", "👍", "lol", "haha", "ha")) {
            return 0.1f
        }

        // High importance: urgent keywords
        if (lowerContent.contains("urgent") ||
            lowerContent.contains("asap") ||
            lowerContent.contains("emergency") ||
            lowerContent.contains("immediately") ||
            lowerContent.contains("right now")) {
            return 0.9f
        }

        // High importance: questions (especially who/what/when/where/how)
        if (lowerContent.contains("?") &&
            (lowerContent.contains("when") ||
             lowerContent.contains("where") ||
             lowerContent.contains("what") ||
             lowerContent.contains("how") ||
             lowerContent.contains("can you") ||
             lowerContent.contains("could you"))) {
            return 0.7f
        }

        // Medium-high: action requests
        if (lowerContent.contains("please") ||
            lowerContent.contains("need") ||
            lowerContent.contains("help") ||
            lowerContent.contains("can we") ||
            lowerContent.contains("should we")) {
            return 0.65f
        }

        return null // No clear heuristic match, need AI analysis
    }

    /**
     * AI-based importance analysis (more sophisticated).
     */
    private suspend fun analyzeWithAI(content: String, senderName: String): Float {
        if (aiEngine.ensureModelLoaded(modelRepository).isFailure) return FALLBACK_SCORE

        val prompt = """
            Analyze this WhatsApp message for importance. Score from 0-10:
            - 0-3: Low importance (casual chat, reactions)
            - 4-6: Medium importance (normal conversation)
            - 7-8: High importance (questions, decisions)
            - 9-10: Critical importance (urgent, action required)

            Sender: $senderName
            Message: "$content"

            Reply with only a single number from 0-10.
        """.trimIndent()

        val response = aiEngine.generate(
            prompt = prompt,
            systemPrompt = "You rate message importance. Answer with a single integer from 0 to 10.",
            maxTokens = 8,
            temperature = 0.1f
        ).getOrElse { error ->
            Timber.w(error, "AI importance analysis failed")
            return FALLBACK_SCORE
        }

        val score = Regex("""\d+""").find(response)?.value?.toIntOrNull() ?: return FALLBACK_SCORE
        return (score / 10.0f).coerceIn(0.0f, 1.0f).also {
            Timber.d("Message importance (AI): $it")
        }
    }

    private companion object {
        const val FALLBACK_SCORE = 0.5f
    }
}
