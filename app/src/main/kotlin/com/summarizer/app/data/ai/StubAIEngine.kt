package com.summarizer.app.data.ai

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.GenerationEvent
import com.summarizer.app.domain.ai.ModelInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of AIEngine for testing and development.
 *
 * TODO: Replace with LlamatikEngine once Llamatik library dependency is resolved.
 *
 * This stub provides mock responses to allow UI and business logic testing
 * without actual LLM inference.
 */
@Singleton
class StubAIEngine @Inject constructor() : AIEngine {

    private var currentModelInfo: ModelInfo? = null

    override suspend fun loadModel(modelPath: String): Result<Unit> = runCatching {
        Timber.d("[STUB] Loading model from: $modelPath")

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw AIEngineError.ModelNotFound(modelPath)
        }

        // Simulate model loading delay
        delay(500)

        currentModelInfo = ModelInfo(
            name = modelFile.nameWithoutExtension,
            path = modelPath,
            contextLength = 2048,
            loadedAt = System.currentTimeMillis()
        )

        Timber.i("[STUB] Model loaded successfully")
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = runCatching {
        if (!isModelLoaded()) {
            throw AIEngineError.ModelNotLoaded
        }

        Timber.d("[STUB] Generating text for prompt length: ${prompt.length}")

        // Simulate inference delay
        delay(2000)

        // Return mock response
        "This is a stub response. The actual AI model integration is pending."
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationEvent> = flow {
        if (!isModelLoaded()) {
            emit(GenerationEvent.Error("No model loaded", AIEngineError.ModelNotLoaded))
            return@flow
        }

        emit(GenerationEvent.Started)
        delay(500)

        val mockTokens = listOf("This ", "is ", "a ", "stub ", "streaming ", "response.")
        for (token in mockTokens) {
            emit(GenerationEvent.TokenGenerated(token))
            delay(200)
        }

        emit(GenerationEvent.Completed(mockTokens.joinToString("")))
    }

    override suspend fun generateJson(
        prompt: String,
        jsonSchema: String?
    ): Result<String> = runCatching {
        if (!isModelLoaded()) {
            throw AIEngineError.ModelNotLoaded
        }

        Timber.d("[STUB] Generating JSON")

        // Simulate inference delay
        delay(2000)

        // Return mock JSON response matching the summary schema
        """
        {
          "overview": "This is a stub summary generated for testing purposes. The conversation covered multiple topics including project planning, technical discussions, and team coordination.",
          "keyTopics": [
            "Project milestones and deadlines",
            "Technical architecture decisions",
            "Team collaboration and responsibilities"
          ],
          "actionItems": [
            {
              "task": "Complete Week 5 AI integration",
              "assignedTo": "Development Team",
              "priority": "high"
            },
            {
              "task": "Test end-to-end summarization flow",
              "priority": "medium"
            }
          ],
          "announcements": [
            "Week 5 development milestone reached",
            "AI integration architecture completed"
          ],
          "participantHighlights": [
            {
              "participant": "Project Lead",
              "contribution": "Provided clear requirements and technical direction"
            },
            {
              "participant": "Developer",
              "contribution": "Implemented clean architecture with domain-driven design"
            }
          ]
        }
        """.trimIndent()
    }

    override fun cancelGeneration() {
        Timber.d("[STUB] Cancel generation called")
    }

    override suspend fun unloadModel() {
        Timber.d("[STUB] Unloading model")
        currentModelInfo = null
    }

    override fun isModelLoaded(): Boolean {
        return currentModelInfo != null
    }

    override fun getModelInfo(): ModelInfo? {
        return currentModelInfo
    }
}
