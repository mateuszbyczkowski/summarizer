package com.summarizer.app.data.ai

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
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

    override fun cancelGeneration() {
        realEngine.cancelGeneration()
        openAIEngine.cancelGeneration()
    }

    override suspend fun unloadModel() {
        // Only the local engine holds resources; OpenAI has nothing to unload
        realEngine.unloadModel()
    }

    override suspend fun isModelLoaded(): Boolean = getActiveEngine().isModelLoaded()

    override suspend fun getModelInfo(): ModelInfo? = getActiveEngine().getModelInfo()
}
