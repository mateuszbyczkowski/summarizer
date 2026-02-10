package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for analyzing message importance using AI.
 *
 * Determines if a message is "important" enough to trigger a notification
 * based on content analysis.
 */
class AnalyzeMessageImportanceUseCase @Inject constructor(
    private val aiEngine: AIEngine,
    private val modelRepository: ModelRepository,
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * Analyze a message and return an importance score (0.0 - 1.0).
     *
     * Scores:
     * - 0.0-0.3: Low importance (casual chat, reactions, etc.)
     * - 0.3-0.6: Medium importance (regular conversation)
     * - 0.6-0.8: High importance (questions, decisions, time-sensitive)
     * - 0.8-1.0: Critical importance (urgent, action required)
     *
     * @param messageContent The message text to analyze
     * @param senderName The name of the sender
     * @return Importance score between 0.0 and 1.0, or null if analysis failed
     */
    suspend fun execute(messageContent: String, senderName: String): Float? {
        return try {
            // Quick heuristic checks for obvious cases
            val heuristicScore = analyzeWithHeuristics(messageContent)
            if (heuristicScore != null) {
                Timber.d("Message importance (heuristic): $heuristicScore")
                return heuristicScore
            }

            // Use AI for more nuanced analysis (only if model is available)
            if (aiEngine.isModelLoaded() || canLoadModel()) {
                analyzeWithAI(messageContent, senderName)
            } else {
                // Fallback to moderate importance if AI not available
                Timber.d("AI not available, using fallback importance: 0.5")
                0.5f
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze message importance")
            // Fallback to medium importance on error
            0.5f
        }
    }

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
        try {
            // Ensure model is loaded
            if (!aiEngine.isModelLoaded()) {
                val model = modelRepository.getDownloadedModel()
                if (model?.localFilePath != null) {
                    aiEngine.loadModel(model.localFilePath).getOrThrow()
                } else {
                    return 0.5f // Fallback
                }
            }

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

            val result = aiEngine.generate(
                prompt = prompt,
                maxTokens = 5,
                temperature = 0.1f
            )

            if (result.isSuccess) {
                val response = result.getOrNull() ?: return 0.5f
                // Extract number from response
                val score = response.trim().toIntOrNull() ?: return 0.5f
                // Normalize to 0.0-1.0
                val normalizedScore = (score / 10.0f).coerceIn(0.0f, 1.0f)
                Timber.d("Message importance (AI): $normalizedScore")
                return normalizedScore
            }
        } catch (e: Exception) {
            Timber.e(e, "AI importance analysis failed")
        }

        return 0.5f // Fallback
    }

    /**
     * Check if we can load a model for analysis.
     */
    private suspend fun canLoadModel(): Boolean {
        val model = modelRepository.getDownloadedModel()
        return model?.localFilePath != null
    }

    /**
     * Check if a message should trigger a notification based on the current threshold.
     */
    suspend fun shouldNotify(messageContent: String, senderName: String): Boolean {
        val score = execute(messageContent, senderName) ?: 0.5f
        val threshold = preferencesRepository.getSmartNotificationThreshold()
        val shouldNotify = score >= threshold

        Timber.d("Should notify: $shouldNotify (score: $score, threshold: $threshold)")
        return shouldNotify
    }
}
