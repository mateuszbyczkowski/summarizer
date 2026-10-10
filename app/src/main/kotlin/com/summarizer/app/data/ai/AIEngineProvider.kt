package com.summarizer.app.data.ai

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic AI engine provider that delegates to the appropriate implementation
 * based on user preferences (Local vs OpenAI).
 *
 * Local models run on one of two on-device engines, chosen by file format:
 * `.litertlm` on [LiteRtEngine] (GPU), `.gguf` on [RealAIEngine] (llama.cpp, CPU). If the GPU
 * cannot load a model, a downloaded CPU model is used instead.
 *
 * This wrapper allows users to switch between AI providers without restarting the app.
 */
@Singleton
class AIEngineProvider @Inject constructor(
    private val realEngine: RealAIEngine,
    private val liteRtEngine: LiteRtEngine,
    private val openAIEngine: OpenAIEngine,
    private val preferencesRepository: PreferencesRepository,
    private val modelRepository: ModelRepository
) : AIEngine {

    /** The local engine currently holding a model, if any. */
    @Volatile
    private var loadedLocalEngine: AIEngine? = null

    private suspend fun getActiveEngine(): AIEngine {
        val provider = preferencesRepository.getAIProvider()
        Timber.d("AIEngineProvider: Active provider is $provider")
        return when (provider) {
            AIProvider.LOCAL -> loadedLocalEngine ?: engineFor(modelRepository.getDownloadedModel()?.localFilePath)
            AIProvider.OPENAI -> openAIEngine
        }
    }

    private fun engineFor(modelPath: String?): AIEngine =
        if (modelPath?.endsWith(".litertlm") == true) liteRtEngine else realEngine

    override suspend fun loadModel(modelPath: String): Result<Unit> {
        if (preferencesRepository.getAIProvider() == AIProvider.OPENAI) {
            return openAIEngine.loadModel(modelPath)
        }

        val engine = engineFor(modelPath)
        // Only one local model in memory at a time
        listOf(realEngine, liteRtEngine).filter { it !== engine }.forEach { it.unloadModel() }

        val result = engine.loadModel(modelPath)
        if (result.isSuccess) {
            loadedLocalEngine = engine
            return result
        }

        // CPU fallback: the GPU could not load the model, use a downloaded GGUF model instead
        if (engine === liteRtEngine) {
            val cpuModelPath = modelRepository.getDownloadedModels()
                .mapNotNull { it.localFilePath }
                .firstOrNull { it.endsWith(".gguf") }
            if (cpuModelPath != null) {
                Timber.w(result.exceptionOrNull(), "GPU model failed to load, falling back to a CPU model")
                liteRtEngine.unloadModel()
                return realEngine.loadModel(cpuModelPath).onSuccess { loadedLocalEngine = realEngine }
            }
        }
        loadedLocalEngine = null
        return result
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
        liteRtEngine.cancelGeneration()
        openAIEngine.cancelGeneration()
    }

    override suspend fun unloadModel() {
        // Only local engines hold resources; OpenAI has nothing to unload
        realEngine.unloadModel()
        liteRtEngine.unloadModel()
        loadedLocalEngine = null
    }

    override suspend fun maxPromptChars(): Int = getActiveEngine().maxPromptChars()

    override suspend fun isModelLoaded(): Boolean = getActiveEngine().isModelLoaded()

    override suspend fun getModelInfo(): ModelInfo? = getActiveEngine().getModelInfo()
}
