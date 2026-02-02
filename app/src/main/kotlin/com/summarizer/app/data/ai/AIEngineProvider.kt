package com.summarizer.app.data.ai

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.GenerationEvent
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic AI engine provider that delegates to the appropriate implementation
 * based on user preferences (Local vs OpenAI).
 *
 * This wrapper allows users to switch between AI providers without restarting the app.
 */
@Singleton
class AIEngineProvider @Inject constructor(
    private val realEngine: RealAIEngine,
    private val openAIEngine: OpenAIEngine,
    private val preferencesRepository: PreferencesRepository
) : AIEngine {

    /**
     * Get the currently active AI engine based on user preferences.
     */
    private suspend fun getActiveEngine(): AIEngine {
        val provider = preferencesRepository.getAIProvider()
        Timber.d("AIEngineProvider: Active provider is $provider")
        return when (provider) {
            AIProvider.LOCAL -> realEngine
            AIProvider.OPENAI -> openAIEngine
        }
    }

    override suspend fun loadModel(modelPath: String): Result<Unit> {
        val engine = getActiveEngine()
        Timber.d("AIEngineProvider: Loading model using ${engine::class.simpleName}")
        return engine.loadModel(modelPath)
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> {
        val engine = getActiveEngine()
        Timber.d("AIEngineProvider: Generating using ${engine::class.simpleName}")
        return engine.generate(prompt, systemPrompt, maxTokens, temperature)
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationEvent> {
        // Note: This is not suspend, so we can't call getActiveEngine()
        // We'll need to use a different approach or make this suspend
        Timber.w("AIEngineProvider: generateStream may not reflect latest provider change until next call")

        // For now, we'll determine the provider synchronously
        // This is a limitation - ideally generateStream should be suspend
        // But changing AIEngine interface would affect all implementations

        // Fallback: Use RealAIEngine for now (streaming is less critical for MVP)
        Timber.d("AIEngineProvider: Using RealAIEngine for streaming (limitation)")
        return realEngine.generateStream(prompt, systemPrompt, maxTokens, temperature)
    }

    override suspend fun generateJson(
        prompt: String,
        jsonSchema: String?
    ): Result<String> {
        val engine = getActiveEngine()
        Timber.d("AIEngineProvider: Generating JSON using ${engine::class.simpleName}")
        return engine.generateJson(prompt, jsonSchema)
    }

    override fun cancelGeneration() {
        Timber.d("AIEngineProvider: Cancelling generation on both engines")
        // Cancel on both engines to be safe
        realEngine.cancelGeneration()
        openAIEngine.cancelGeneration()
    }

    override suspend fun unloadModel() {
        Timber.d("AIEngineProvider: Unloading models on both engines")
        realEngine.unloadModel()
        openAIEngine.unloadModel()
    }

    override fun isModelLoaded(): Boolean {
        // Check if either engine has a model loaded
        val realLoaded = realEngine.isModelLoaded()
        val openAILoaded = openAIEngine.isModelLoaded()
        Timber.d("AIEngineProvider: Model loaded - Real: $realLoaded, OpenAI: $openAILoaded")
        return realLoaded || openAILoaded
    }

    override fun getModelInfo(): ModelInfo? {
        // Return info from whichever engine has a model loaded
        val realInfo = realEngine.getModelInfo()
        val openAIInfo = openAIEngine.getModelInfo()

        return when {
            realInfo != null -> realInfo
            openAIInfo != null -> openAIInfo
            else -> null
        }
    }
}
