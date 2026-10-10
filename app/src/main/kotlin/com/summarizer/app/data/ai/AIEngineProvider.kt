package com.summarizer.app.data.ai

import com.summarizer.app.BuildConfig
import com.summarizer.app.data.download.ModelSetupManager
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import dagger.Lazy
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic AI engine provider that delegates to the appropriate implementation
 * based on user preferences (Local vs OpenAI).
 *
 * Local models run on one of two on-device engines, chosen by file format:
 * `.litertlm` on [LiteRtEngine] (GPU), `.gguf` on [RealAIEngine] (llama.cpp, CPU). If the GPU
 * cannot load a model, a downloaded CPU model is used instead. Debug builds also offer
 * [DemoEngine], which streams a fixed summary without a model file.
 *
 * This wrapper allows users to switch between AI providers without restarting the app.
 */
@Singleton
class AIEngineProvider @Inject constructor(
    private val realEngine: RealAIEngine,
    private val liteRtEngine: LiteRtEngine,
    private val openAIEngine: OpenAIEngine,
    private val demoEngine: DemoEngine,
    private val preferencesRepository: PreferencesRepository,
    private val modelRepository: ModelRepository,
    // Lazy: the setup manager itself depends on the active engine
    private val modelSetupManager: Lazy<ModelSetupManager>
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

    private fun engineFor(modelPath: String?): AIEngine = when {
        BuildConfig.DEBUG && modelPath == DemoEngine.MODEL_PATH -> demoEngine
        modelPath?.endsWith(".litertlm") == true -> liteRtEngine
        else -> realEngine
    }

    private val localEngines get() = listOf(realEngine, liteRtEngine, demoEngine)

    override suspend fun loadModel(modelPath: String): Result<Unit> {
        if (preferencesRepository.getAIProvider() == AIProvider.OPENAI) {
            return openAIEngine.loadModel(modelPath)
        }

        val engine = engineFor(modelPath)
        // Only one local model in memory at a time
        localEngines.filter { it !== engine }.forEach { it.unloadModel() }

        val result = engine.loadModel(modelPath)
        if (result.isSuccess) {
            loadedLocalEngine = engine
            return result
        }

        // CPU fallback: the GPU could not load the model, use a downloaded GGUF model instead
        if (engine === liteRtEngine) {
            val cpuModel = modelRepository.getDownloadedModels()
                .firstOrNull { it.localFilePath?.endsWith(".gguf") == true && File(it.localFilePath).exists() }
            val cpuModelPath = cpuModel?.localFilePath
            if (cpuModelPath != null) {
                Timber.w(result.exceptionOrNull(), "GPU model failed to load, falling back to ${cpuModel.name} on the CPU")
                liteRtEngine.unloadModel()
                return realEngine.loadModel(cpuModelPath).onSuccess {
                    loadedLocalEngine = realEngine
                    // Make the CPU model the active one, so later runs do not retry the GPU
                    modelRepository.markAsDownloaded(cpuModel.id, cpuModelPath, System.currentTimeMillis())
                }
            }
            // No CPU model yet: download one in the background (Home shows its progress); it
            // becomes the active model when done, so the next summary works
            Timber.w(result.exceptionOrNull(), "GPU model failed to load and no CPU model is downloaded; setting one up")
            modelSetupManager.get().startCpuFallback()
        }
        loadedLocalEngine = null
        return result
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float,
        onPartial: ((String) -> Unit)?
    ): Result<String> {
        val engine = getActiveEngine()
        Timber.d("AIEngineProvider: Generating using ${engine::class.simpleName}")
        return engine.generate(prompt, systemPrompt, maxTokens, temperature, onPartial)
    }

    override fun cancelGeneration() {
        localEngines.forEach { it.cancelGeneration() }
        openAIEngine.cancelGeneration()
    }

    override suspend fun unloadModel() {
        // Only local engines hold resources; OpenAI has nothing to unload
        localEngines.forEach { it.unloadModel() }
        loadedLocalEngine = null
    }

    override suspend fun maxPromptChars(): Int = getActiveEngine().maxPromptChars()

    override suspend fun isModelLoaded(): Boolean = getActiveEngine().isModelLoaded()

    override suspend fun getModelInfo(): ModelInfo? = getActiveEngine().getModelInfo()

    /**
     * The catalog name of the loaded local model with where it runs ("Gemma 4 E2B · GPU"), or
     * the OpenAI model.
     */
    override suspend fun engineName(): String? {
        val engine = getActiveEngine()
        if (engine === openAIEngine || engine === demoEngine) return engine.engineName()
        val info = engine.getModelInfo() ?: return null
        val catalogName = modelRepository.getDownloadedModels().firstOrNull { it.localFilePath == info.path }?.name
        // Catalog names carry the backend in parentheses: "Gemma 4 E2B (GPU)"
        val name = catalogName?.substringBefore(" (")?.trim() ?: info.name
        return "$name · ${if (engine === liteRtEngine) "GPU" else "CPU"}"
    }
}
