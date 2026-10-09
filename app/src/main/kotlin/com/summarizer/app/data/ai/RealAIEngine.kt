package com.summarizer.app.data.ai

import com.llamatik.library.platform.LlamaBridge
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.util.suspendRunCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI engine using Llamatik (llama.cpp).
 *
 * LlamaBridge holds a single global llama.cpp context that is not thread-safe, so every
 * native call runs on one dedicated thread. This serializes load, generate and unload.
 */
@Singleton
class RealAIEngine @Inject constructor() : AIEngine {

    @Volatile
    private var currentModelInfo: ModelInfo? = null
    private val bridge = LlamaBridge
    private val llamaDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "llama-native")
    }.asCoroutineDispatcher()

    companion object {
        private const val GENERATION_TIMEOUT_MS = 120_000L // 2 minutes
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_REPEAT_PENALTY = 1.1f

        // Catalog models support 120K-256K natively; we cap on-device at 16K to keep KV-cache RAM reasonable on
        // mid-range phones. Llamatik's generateWithContext reads the model's embedded ChatML
        // chat template from the GGUF, so no manual <|im_start|>/<|im_end|> wrapping is needed here.
        const val MODEL_CONTEXT_TOKENS = 16_384

        // Runtime settings required since Llamatik 1.x; values follow llama.cpp defaults
        private const val MAX_THREADS = 4
        private const val BATCH_SIZE = 512
    }

    private fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float = DEFAULT_TOP_P,
        topK: Int = DEFAULT_TOP_K,
        repeatPenalty: Float = DEFAULT_REPEAT_PENALTY
    ) {
        bridge.updateGenerateParams(
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP,
            topK = topK,
            repeatPenalty = repeatPenalty,
            contextLength = MODEL_CONTEXT_TOKENS,
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS),
            useMmap = true,
            flashAttention = false,
            batchSize = BATCH_SIZE
        )
    }

    override suspend fun loadModel(modelPath: String): Result<Unit> = suspendRunCatching {
        withContext(llamaDispatcher) {
            Timber.d("Loading model from: $modelPath")

            // Unload existing model to prevent resource leak
            if (currentModelInfo != null) {
                bridge.shutdown()
                currentModelInfo = null
            }

            if (modelPath.isBlank()) {
                throw AIEngineError.ModelLoadFailed("No model downloaded. Please download a model first.")
            }
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw AIEngineError.ModelNotFound(modelPath)
            }

            Timber.i("Model file exists (${modelFile.length() / 1024 / 1024}MB), initializing...")

            // Context settings must be in place before the llama context is created
            updateGenerateParams(DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS)
            if (!bridge.initGenerateModel(modelPath)) {
                throw AIEngineError.ModelLoadFailed("Llamatik initGenerateModel returned false")
            }
            updateGenerateParams(DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS)

            currentModelInfo = ModelInfo(
                name = modelFile.nameWithoutExtension,
                path = modelPath,
                contextLength = MODEL_CONTEXT_TOKENS,
                loadedAt = System.currentTimeMillis()
            )
            Timber.i("Model loaded successfully: ${modelFile.name}")
        }
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = suspendRunCatching {
        Timber.d("Generating text (${prompt.length} chars, max tokens: $maxTokens, temp: $temperature)")

        val result = runCancellable {
            if (currentModelInfo == null) throw AIEngineError.ModelNotLoaded
            updateGenerateParams(temperature, maxTokens)
            if (systemPrompt != null) {
                bridge.generateWithContext(
                    systemPrompt = systemPrompt,
                    contextBlock = "",
                    userPrompt = prompt
                )
            } else {
                bridge.generate(prompt)
            }
        }

        if (result.isNullOrEmpty()) {
            throw AIEngineError.InvalidResponse("Empty response from model")
        }
        Timber.i("Generation complete (${result.length} chars)")
        result
    }

    /**
     * Runs a blocking native generation on the llama thread with a timeout.
     *
     * Coroutine cancellation cannot interrupt JNI code, so on timeout or cancellation we ask
     * llama.cpp to stop and wait for the native call to return before releasing the thread.
     */
    private suspend fun <T> runCancellable(block: () -> T): T = coroutineScope {
        val nativeCall = async(llamaDispatcher) { block() }
        try {
            withTimeout(GENERATION_TIMEOUT_MS) { nativeCall.await() }
        } catch (e: CancellationException) {
            if (nativeCall.isActive) {
                Timber.w("Generation cancelled or timed out, stopping native generation")
                bridge.nativeCancelGenerate()
            }
            if (e is TimeoutCancellationException) throw AIEngineError.GenerationTimeout
            throw e
        }
    }

    override fun cancelGeneration() {
        bridge.nativeCancelGenerate()
    }

    override suspend fun unloadModel() {
        withContext(llamaDispatcher) {
            if (currentModelInfo != null) {
                bridge.shutdown()
                currentModelInfo = null
                Timber.i("Model unloaded successfully")
            }
        }
    }

    override suspend fun isModelLoaded(): Boolean = currentModelInfo != null

    override suspend fun getModelInfo(): ModelInfo? = currentModelInfo
}
