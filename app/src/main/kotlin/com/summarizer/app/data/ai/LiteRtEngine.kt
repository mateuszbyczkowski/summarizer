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
        private const val WAIT_SLICE_MS = 500L
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

    /** When cancelGeneration() was last called for the running generation; 0 when not cancelled. */
    @Volatile
    private var cancelRequestedAt = 0L

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
        val pieces = StringBuilder()
        var lastChunk = ""
        var chunkCount = 0
        // LiteRT-LM 0.18 does not document whether a message carries only the new text or
        // everything so far. Treat it as cumulative only while every message extends the
        // previous one; a single exception means the messages are pieces to be joined.
        var looksCumulative = true
        val done = CountDownLatch(1)
        var error: Throwable? = null
        val started = SystemClock.elapsedRealtime()
        var firstTextAt = 0L
        cancelRequestedAt = 0L

        fun text(): String = if (looksCumulative && chunkCount > 1) lastChunk else pieces.toString()

        conversation.sendMessageAsync(prompt, object : MessageCallback {
            override fun onMessage(message: Message) {
                val chunk = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (chunk.isEmpty()) return
                if (firstTextAt == 0L) firstTextAt = SystemClock.elapsedRealtime()
                if (chunkCount > 0 && !(chunk.length >= lastChunk.length && chunk.startsWith(lastChunk))) {
                    looksCumulative = false
                }
                pieces.append(chunk)
                lastChunk = chunk
                chunkCount++
                onPartial?.invoke(stripReasoning(text()))
            }

            override fun onDone() = done.countDown()

            override fun onError(throwable: Throwable) {
                error = throwable
                done.countDown()
            }
        })

        // The caller's timeout or cancellation calls cancelGeneration(); after that, give
        // LiteRT-LM a short moment to finish instead of blocking this thread for minutes
        val deadline = started + GENERATION_TIMEOUT_MS + STOPPED_WAIT_MS
        while (!done.await(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)) {
            val now = SystemClock.elapsedRealtime()
            val cancelledAt = cancelRequestedAt
            if (now > deadline || (cancelledAt > 0 && now - cancelledAt > STOPPED_WAIT_MS)) {
                Timber.w("LiteRT-LM did not report completion")
                break
            }
        }

        val now = SystemClock.elapsedRealtime()
        val promptMs = if (firstTextAt > 0) firstTextAt - started else now - started
        val result = text()
        Timber.i("LiteRT-LM generation: prompt ${prompt.length} chars read in ${promptMs}ms, ${result.length} chars in ${now - started}ms")
        // A failure midway leaves a truncated reply; never save that as a complete summary
        error?.let { throw AIEngineError.GenerationFailed(it.message ?: "LiteRT-LM generation failed", it) }
        return result
    }

    override suspend fun maxPromptChars(): Int = MAX_PROMPT_CHARS

    override fun cancelGeneration() {
        synchronized(conversationLock) {
            cancelRequestedAt = SystemClock.elapsedRealtime()
            activeConversation?.cancelProcess()
        }
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
