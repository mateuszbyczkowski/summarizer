package com.summarizer.app.data.ai

import android.os.SystemClock
import com.llamatik.library.platform.GenStream
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    /** Chat template embedded in the loaded GGUF; read on the llama thread at load time. */
    @Volatile
    private var chatTemplate: String? = null
    private val bridge = LlamaBridge
    private val llamaDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "llama-native")
    }.asCoroutineDispatcher()

    companion object {
        // On-device generation runs in a foreground job; long prompts on slow phones can take minutes
        private const val GENERATION_TIMEOUT_MS = 10 * 60_000L
        private const val MAX_CHARS_PER_TOKEN = 8
        private val UNCLOSED_THINKING = Regex("<think>[\\s\\S]*$")
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_REPEAT_PENALTY = 1.1f

        // Catalog models support far more, but phones process prompts slowly on the CPU and the
        // KV cache grows with context. 8K tokens fits a long day of chat (see maxPromptChars).
        // Llamatik applies the model's own chat template from the GGUF.
        const val MODEL_CONTEXT_TOKENS = 8_192

        // ~3.5K tokens of transcript at ~2.4 chars/token: leaves room for instructions and output
        // and keeps prompt processing to well under a minute on mid-range phones
        private const val MAX_PROMPT_CHARS = 8_500

        // Measured on a Dimensity 900 (LocalModelBenchmark): generation is fastest with 6 of 8
        // cores; leaving 2 cores free keeps the UI responsive. Batch size and flash attention
        // made no measurable difference.
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 6
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
            numThreads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(MIN_THREADS, MAX_THREADS),
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

            chatTemplate = bridge.getModelChatTemplate()
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
        val raw = runCancellable {
            if (currentModelInfo == null) throw AIEngineError.ModelNotLoaded
            updateGenerateParams(temperature, maxTokens)
            streamGeneration(prompt, systemPrompt.orEmpty(), maxTokens)
        }

        val result = stripReasoning(raw)
        if (result.isEmpty()) {
            throw AIEngineError.GenerationFailed(
                if (raw.isBlank()) "The model returned an empty response"
                else "The model only produced reasoning and no answer. Try again or choose another model."
            )
        }
        result
    }

    /**
     * Streams a generation on the llama thread. Streaming lets us stop a model that ignores the
     * token limit (instead of running until the timeout) and measure prompt processing vs.
     * generation speed. Logs contain sizes and timings only, never content.
     */
    private fun streamGeneration(prompt: String, systemPrompt: String, maxTokens: Int): String {
        val output = StringBuilder()
        val done = CountDownLatch(1)
        var error: String? = null
        val startedAt = SystemClock.elapsedRealtime()
        var firstTokenAt = 0L
        // Generous upper bound in characters for maxTokens; only hit by runaway generations
        val maxChars = maxTokens * MAX_CHARS_PER_TOKEN

        val callback = object : GenStream {
            override fun onDelta(text: String) {
                if (firstTokenAt == 0L) firstTokenAt = SystemClock.elapsedRealtime()
                output.append(text)
                if (output.length > maxChars) {
                    Timber.w("Generation exceeded $maxChars chars, stopping")
                    bridge.nativeCancelGenerate()
                }
            }

            override fun onComplete() = done.countDown()

            override fun onError(message: String) {
                error = message
                done.countDown()
            }
        }

        val chatPrompt = buildChatPrompt(systemPrompt, prompt)
        if (chatPrompt != null) {
            bridge.generateStream(chatPrompt, callback)
        } else {
            // No usable chat template in the GGUF: let Llamatik apply its fallback format
            bridge.generateStreamWithContext(
                systemPrompt = systemPrompt,
                contextBlock = "",
                userPrompt = prompt,
                callback = callback
            )
        }
        // The native call normally returns after its last callback; wait in case it streams asynchronously
        done.await(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        val now = SystemClock.elapsedRealtime()
        val promptMs = if (firstTokenAt > 0) firstTokenAt - startedAt else now - startedAt
        val generationMs = if (firstTokenAt > 0) now - firstTokenAt else 0
        Timber.i(
            "Generation: prompt ${prompt.length} chars processed in ${promptMs}ms, " +
                "${output.length} chars generated in ${generationMs}ms"
        )

        error?.let { if (output.isEmpty()) throw AIEngineError.GenerationFailed(it) }
        return output.toString()
    }

    /**
     * Applies the model's own chat template. For reasoning models (Qwen3/3.5 templates use
     * <think>), an empty reasoning block is placed after the assistant prefix, which is how
     * `enable_thinking=false` renders: the model answers directly instead of spending the whole
     * token budget on reasoning.
     */
    private fun buildChatPrompt(systemPrompt: String, userPrompt: String): String? {
        val messages = buildList {
            if (systemPrompt.isNotBlank()) add("system" to systemPrompt)
            add("user" to userPrompt)
        }
        val prompt = bridge.applyChatTemplate(messages, addAssistantPrefix = true) ?: return null
        if (chatTemplate?.contains("<think>") != true) return prompt
        Timber.d("Reasoning model: answering without thinking")
        return if (prompt.trimEnd().endsWith("<think>")) {
            prompt.trimEnd() + "\n\n</think>\n\n"
        } else {
            prompt + "<think>\n\n</think>\n\n"
        }
    }

    /** Removes reasoning, including a block cut off before it was closed. */
    private fun stripReasoning(text: String): String {
        var answer = text
        if ("</think>" in answer) answer = answer.substringAfterLast("</think>")
        answer = answer.replace(UNCLOSED_THINKING, "")
        return answer.trim()
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
                chatTemplate = null
                Timber.i("Model unloaded successfully")
            }
        }
    }

    override suspend fun maxPromptChars(): Int = MAX_PROMPT_CHARS

    override suspend fun isModelLoaded(): Boolean = currentModelInfo != null

    override suspend fun getModelInfo(): ModelInfo? = currentModelInfo
}
