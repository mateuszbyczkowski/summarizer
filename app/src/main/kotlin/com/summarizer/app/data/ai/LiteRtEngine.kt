package com.summarizer.app.data.ai

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.ai.stripReasoning
import com.summarizer.app.util.suspendRunCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * On-device engine for `.litertlm` models (Gemma 4) using Google LiteRT-LM on the GPU.
 *
 * On a Dimensity 900 it reads prompts ~10-40x faster than llama.cpp (LocalModelBenchmark vs
 * LiteRtBenchmark). Native calls run on one dedicated thread; each summary uses a fresh
 * conversation so no context leaks between threads.
 */
@Singleton
class LiteRtEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AIEngine {

    companion object {
        // Prompt plus output; the transcript budget below leaves room for instructions and output
        private const val MAX_NUM_TOKENS = 8_192
        // ~6K tokens of transcript at ~2.4 chars/token; reads in well under a minute on the GPU
        private const val MAX_PROMPT_CHARS = 14_000
        private const val GENERATION_TIMEOUT_MS = 5 * 60_000L
        private const val STOPPED_WAIT_MS = 10_000L
        private const val TOP_K = 40
        private const val TOP_P = 0.9
    }

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litert-native")
    }.asCoroutineDispatcher()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var currentModelInfo: ModelInfo? = null

    private val generationMutex = Mutex()
    private val conversationLock = Any()

    @Volatile
    private var activeConversation: Conversation? = null

    override suspend fun loadModel(modelPath: String): Result<Unit> = suspendRunCatching {
        withContext(dispatcher) {
            val file = File(modelPath)
            if (!file.exists()) throw AIEngineError.ModelNotFound(modelPath)
            if (currentModelInfo?.path == modelPath) return@withContext

            closeEngine()
            val started = SystemClock.elapsedRealtime()
            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    maxNumTokens = MAX_NUM_TOKENS,
                    // Compiled GPU programs are cached here, which speeds up later loads
                    cacheDir = context.cacheDir.path
                )
            )
            try {
                newEngine.initialize()
            } catch (e: Exception) {
                newEngine.close()
                Timber.e(e, "LiteRT-LM GPU initialization failed")
                throw AIEngineError.ModelLoadFailed(
                    "This phone's GPU could not run the model. Choose LFM2.5 (runs on the CPU) in Manage Models.",
                    e
                )
            }
            engine = newEngine
            currentModelInfo = ModelInfo(
                name = file.nameWithoutExtension,
                path = modelPath,
                contextLength = MAX_NUM_TOKENS
            )
            Timber.i("LiteRT-LM model loaded in ${SystemClock.elapsedRealtime() - started}ms")
        }
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float,
        onPartial: ((String) -> Unit)?
    ): Result<String> = suspendRunCatching {
        // One generation at a time: waiting for another summary must not count against this
        // one's timeout, and a timeout must only cancel this summary's conversation
        val raw = generationMutex.withLock { coroutineScope {
            val nativeCall = async(dispatcher) {
                val loaded = engine ?: throw AIEngineError.ModelNotLoaded
                val config = ConversationConfig(
                    systemInstruction = systemPrompt?.let { Contents.of(it) },
                    samplerConfig = SamplerConfig(topK = TOP_K, topP = TOP_P, temperature = temperature.toDouble(), seed = 0),
                    maxOutputToken = maxTokens
                )
                loaded.createConversation(config).use { conversation ->
                    synchronized(conversationLock) { activeConversation = conversation }
                    try {
                        streamReply(conversation, prompt, onPartial)
                    } finally {
                        // Cleared before use{} closes the conversation, so cancel never sees a closed one
                        synchronized(conversationLock) { activeConversation = null }
                    }
                }
            }
            try {
                withTimeout(GENERATION_TIMEOUT_MS) { nativeCall.await() }
            } catch (e: CancellationException) {
                // Coroutine cancellation cannot interrupt native code; ask LiteRT-LM to stop
                cancelGeneration()
                if (e is TimeoutCancellationException) throw AIEngineError.GenerationTimeout
                throw e
            }
        } }

        val result = stripReasoning(raw)
        if (result.isEmpty()) throw AIEngineError.InvalidResponse("Empty response from model")
        result
    }

    /**
     * Sends the prompt and blocks this (native) thread until the reply is complete, reporting
     * the text so far to [onPartial]. Logs contain sizes and timings only, never content.
     */
    private fun streamReply(conversation: Conversation, prompt: String, onPartial: ((String) -> Unit)?): String {
        val output = StringBuilder()
        val done = CountDownLatch(1)
        var error: Throwable? = null
        // LiteRT-LM 0.18 sends each new piece of text; the API does not document this, so a
        // message that repeats everything so far (cumulative) is detected on the second one
        var cumulative: Boolean? = null
        val started = SystemClock.elapsedRealtime()
        var firstTextAt = 0L

        conversation.sendMessageAsync(prompt, object : MessageCallback {
            override fun onMessage(message: Message) {
                val chunk = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (chunk.isEmpty()) return
                if (firstTextAt == 0L) firstTextAt = SystemClock.elapsedRealtime()
                if (cumulative == null && output.isNotEmpty()) {
                    cumulative = chunk.length > output.length && chunk.startsWith(output)
                }
                if (cumulative == true) output.setLength(0)
                output.append(chunk)
                onPartial?.invoke(stripReasoning(output.toString()))
            }

            override fun onDone() = done.countDown()

            override fun onError(throwable: Throwable) {
                error = throwable
                done.countDown()
            }
        })

        if (!done.await(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            conversation.cancelProcess()
            if (!done.await(STOPPED_WAIT_MS, TimeUnit.MILLISECONDS)) Timber.w("LiteRT-LM did not report completion after cancel")
        }

        val now = SystemClock.elapsedRealtime()
        val promptMs = if (firstTextAt > 0) firstTextAt - started else now - started
        Timber.i("LiteRT-LM generation: prompt ${prompt.length} chars read in ${promptMs}ms, ${output.length} chars in ${now - started}ms")
        error?.let { if (output.isEmpty()) throw AIEngineError.GenerationFailed(it.message ?: "LiteRT-LM generation failed", it) }
        return output.toString()
    }

    override suspend fun maxPromptChars(): Int = MAX_PROMPT_CHARS

    override fun cancelGeneration() {
        synchronized(conversationLock) { activeConversation?.cancelProcess() }
    }

    override suspend fun unloadModel() {
        withContext(dispatcher) { closeEngine() }
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
        currentModelInfo = null
    }

    override suspend fun isModelLoaded(): Boolean = engine != null

    override suspend fun getModelInfo(): ModelInfo? = currentModelInfo
}
