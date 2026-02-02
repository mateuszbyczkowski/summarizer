package com.summarizer.app.data.ai

import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.GenerationEvent
import com.summarizer.app.domain.ai.ModelInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real AI engine implementation using Llamatik 0.13.0 library.
 *
 * Replaces StubAIEngine for actual on-device LLM inference using llama.cpp.
 */
@Singleton
class RealAIEngine @Inject constructor() : AIEngine {

    @Volatile
    private var currentModelInfo: ModelInfo? = null
    private val bridge = LlamaBridge
    private val modelLock = Any()

    companion object {
        private const val GENERATION_TIMEOUT_MS = 120_000L // 2 minutes
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_REPEAT_PENALTY = 1.1f
    }

    override suspend fun loadModel(modelPath: String): Result<Unit> = runCatching {
        synchronized(modelLock) {
            Timber.d("Loading model from: $modelPath")

            // Unload existing model to prevent resource leak
            if (currentModelInfo != null) {
                Timber.w("Model already loaded, unloading first to prevent resource leak")
                bridge.shutdown()
                currentModelInfo = null
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Timber.e("Model file not found: $modelPath")
                throw AIEngineError.ModelNotFound(modelPath)
            }

            Timber.i("Model file exists (${modelFile.length() / 1024 / 1024}MB), initializing...")

            // Initialize model with Llamatik
            val success = bridge.initGenerateModel(modelPath)
            if (!success) {
                Timber.e("Failed to initialize model")
                throw AIEngineError.ModelLoadFailed("Llamatik initGenerateModel returned false")
            }

            // Set default generation parameters
            bridge.updateGenerateParams(
                temperature = DEFAULT_TEMPERATURE,
                maxTokens = DEFAULT_MAX_TOKENS,
                topP = DEFAULT_TOP_P,
                topK = DEFAULT_TOP_K,
                repeatPenalty = DEFAULT_REPEAT_PENALTY
            )

            currentModelInfo = ModelInfo(
                name = modelFile.nameWithoutExtension,
                path = modelPath,
                contextLength = 2048, // TinyLlama default
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
    ): Result<String> = runCatching {
        if (!isModelLoaded()) {
            throw AIEngineError.ModelNotLoaded
        }

        Timber.d("Generating text (${prompt.length} chars, max tokens: $maxTokens, temp: $temperature)")

        // Update generation parameters
        bridge.updateGenerateParams(
            temperature = temperature,
            maxTokens = maxTokens,
            topP = DEFAULT_TOP_P,
            topK = DEFAULT_TOP_K,
            repeatPenalty = DEFAULT_REPEAT_PENALTY
        )

        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                val result = if (systemPrompt != null) {
                    // Use generateWithContext for system prompts
                    bridge.generateWithContext(
                        systemPrompt = systemPrompt,
                        contextBlock = "", // Empty context for now
                        userPrompt = prompt
                    )
                } else {
                    bridge.generate(prompt)
                }

                if (result.isNullOrEmpty()) {
                    throw AIEngineError.InvalidResponse("Empty response from model")
                }

                Timber.i("Generation complete (${result.length} chars)")
                result
            }
        } catch (e: Exception) {
            Timber.w(e, "Generation failed or timed out, cancelling")
            bridge.nativeCancelGenerate()
            throw e
        }
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationEvent> = callbackFlow {
        if (!isModelLoaded()) {
            send(GenerationEvent.Error("No model loaded", AIEngineError.ModelNotLoaded))
            close()
            return@callbackFlow
        }

        send(GenerationEvent.Started)
        Timber.d("Starting streaming generation")

        // Update generation parameters
        bridge.updateGenerateParams(
            temperature = temperature,
            maxTokens = maxTokens,
            topP = DEFAULT_TOP_P,
            topK = DEFAULT_TOP_K,
            repeatPenalty = DEFAULT_REPEAT_PENALTY
        )

        val fullText = StringBuilder()
        var generationComplete = false

        val callback = object : GenStream {
            override fun onDelta(token: String) {
                fullText.append(token)
                val result = trySend(GenerationEvent.TokenGenerated(token))
                if (result.isFailure) {
                    Timber.w("Failed to send token, channel may be closed: ${result.exceptionOrNull()}")
                }
            }

            override fun onComplete() {
                generationComplete = true
                val result = trySend(GenerationEvent.Completed(fullText.toString()))
                if (result.isFailure) {
                    Timber.w("Failed to send completion event: ${result.exceptionOrNull()}")
                }
                Timber.i("Streaming generation complete (${fullText.length} chars)")
                close()
            }

            override fun onError(error: String) {
                generationComplete = true
                Timber.e("Streaming generation error: $error")
                val result = trySend(GenerationEvent.Error(error, AIEngineError.GenerationFailed(error)))
                if (result.isFailure) {
                    Timber.w("Failed to send error event: ${result.exceptionOrNull()}")
                }
                close()
            }
        }

        try {
            if (systemPrompt != null) {
                bridge.generateStreamWithContext(
                    systemPrompt = systemPrompt,
                    contextBlock = "", // Empty context for now
                    userPrompt = prompt,
                    callback = callback
                )
            } else {
                bridge.generateStream(prompt, callback)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during streaming generation")
            bridge.nativeCancelGenerate()
            trySend(GenerationEvent.Error(e.message ?: "Unknown error", e))
            close()
        }

        awaitClose {
            Timber.d("Streaming flow closed")
            if (!generationComplete) {
                bridge.nativeCancelGenerate()
            }
        }
    }

    override suspend fun generateJson(
        prompt: String,
        jsonSchema: String?
    ): Result<String> = runCatching {
        if (!isModelLoaded()) {
            throw AIEngineError.ModelNotLoaded
        }

        Timber.d("Generating JSON (${prompt.length} chars, schema: ${jsonSchema != null})")

        // Use low temperature for structured output
        bridge.updateGenerateParams(
            temperature = 0.2f, // Low temperature for consistent JSON
            maxTokens = 2048,
            topP = 0.95f,
            topK = 40,
            repeatPenalty = 1.1f
        )

        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                // IMPORTANT: Do NOT use generateJsonWithContext - it crashes with grammar errors
                // Instead, use regular generation with strong JSON prompt
                val systemPrompt = "You are a helpful assistant that ALWAYS responds with valid JSON only. Never include explanations, markdown formatting, or any text outside the JSON object."

                val enhancedPrompt = if (jsonSchema != null) {
                    "$prompt\n\nRespond with a valid JSON object matching this structure:\n$jsonSchema\n\nIMPORTANT: Output ONLY the JSON object, no markdown, no explanations."
                } else {
                    "$prompt\n\nRespond with ONLY valid JSON, no markdown or explanations."
                }

                // Use regular text generation instead of JSON grammar (which crashes)
                val result = bridge.generateWithContext(
                    systemPrompt = systemPrompt,
                    contextBlock = "",
                    userPrompt = enhancedPrompt
                )

                if (result.isNullOrEmpty()) {
                    throw AIEngineError.InvalidResponse("Empty JSON response from model")
                }

                Timber.i("JSON generation complete (${result.length} chars)")
                result
            }
        } catch (e: Exception) {
            Timber.w(e, "JSON generation failed or timed out, cancelling")
            bridge.nativeCancelGenerate()
            throw e
        }
    }

    override fun cancelGeneration() {
        Timber.d("Cancelling generation")
        bridge.nativeCancelGenerate()
    }

    override suspend fun unloadModel() {
        synchronized(modelLock) {
            Timber.d("Unloading model")
            bridge.shutdown()
            currentModelInfo = null
            Timber.i("Model unloaded successfully")
        }
    }

    override fun isModelLoaded(): Boolean {
        return currentModelInfo != null
    }

    override fun getModelInfo(): ModelInfo? {
        return currentModelInfo
    }
}
