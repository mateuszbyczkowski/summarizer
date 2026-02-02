package com.summarizer.app.data.ai

import com.summarizer.app.data.api.ChatCompletionRequest
import com.summarizer.app.data.api.ChatMessage
import com.summarizer.app.data.api.OpenAIService
import com.summarizer.app.data.api.ResponseFormat
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.GenerationEvent
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI API-based AI engine implementation.
 *
 * Uses gpt-4o-mini for cost-effective, high-quality message summarization.
 * Requires API key configuration and internet connection.
 */
@Singleton
class OpenAIEngine @Inject constructor(
    private val openAIService: OpenAIService,
    private val preferencesRepository: PreferencesRepository
) : AIEngine {

    companion object {
        private const val MODEL = "gpt-4o-mini"
        private const val TIMEOUT_MS = 30_000L // 30 seconds
        private const val CONTEXT_LENGTH = 128_000 // gpt-4o-mini context window
    }

    private var apiKey: String? = null

    override suspend fun loadModel(modelPath: String): Result<Unit> = runCatching {
        Timber.d("OpenAIEngine: Loading (validating API key)")

        // For OpenAI, "loading" means validating the API key
        val key = preferencesRepository.getOpenAIApiKey()
        if (key.isNullOrBlank()) {
            Timber.e("No OpenAI API key configured")
            throw AIEngineError.ModelLoadFailed("No OpenAI API key configured. Please add your API key in Settings.")
        }

        apiKey = key
        Timber.i("OpenAI API key loaded successfully")
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = runCatching {
        if (apiKey == null) {
            throw AIEngineError.ModelNotLoaded
        }

        Timber.d("OpenAI: Generating text (${prompt.length} chars, max tokens: $maxTokens, temp: $temperature)")

        val messages = buildList {
            if (systemPrompt != null) {
                add(ChatMessage(role = "system", content = systemPrompt))
            }
            add(ChatMessage(role = "user", content = prompt))
        }

        val request = ChatCompletionRequest(
            model = MODEL,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = false
        )

        try {
            withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val response = openAIService.createChatCompletion(
                        authorization = "Bearer $apiKey",
                        request = request
                    )

                    if (response.choices.isEmpty()) {
                        throw AIEngineError.InvalidResponse("No choices in OpenAI response")
                    }

                    val generatedText = response.choices[0].message.content

                    Timber.i("OpenAI: Generation complete (${generatedText.length} chars, ${response.usage.totalTokens} tokens)")
                    Timber.d("OpenAI: Token usage - Prompt: ${response.usage.promptTokens}, Completion: ${response.usage.completionTokens}")

                    generatedText
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "OpenAI: Generation failed")
            when {
                e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> {
                    throw AIEngineError.GenerationFailed("Invalid API key. Please check your OpenAI API key in Settings.", e)
                }
                e.message?.contains("429") == true || e.message?.contains("rate limit") == true -> {
                    throw AIEngineError.GenerationFailed("Rate limit exceeded. Please try again later.", e)
                }
                e.message?.contains("timeout") == true -> {
                    throw AIEngineError.GenerationTimeout
                }
                else -> {
                    throw AIEngineError.GenerationFailed("OpenAI API error: ${e.message}", e)
                }
            }
        }
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationEvent> = callbackFlow {
        if (apiKey == null) {
            send(GenerationEvent.Error("No API key loaded", AIEngineError.ModelNotLoaded))
            close()
            return@callbackFlow
        }

        send(GenerationEvent.Started)
        Timber.d("OpenAI: Starting streaming generation")

        // TODO: Implement streaming with Server-Sent Events (SSE) parser
        // For now, fall back to non-streaming
        try {
            val result = generate(prompt, systemPrompt, maxTokens, temperature).getOrThrow()
            send(GenerationEvent.Completed(result))
            Timber.i("OpenAI: Streaming complete (fallback to non-streaming)")
        } catch (e: Exception) {
            Timber.e(e, "OpenAI: Streaming failed")
            send(GenerationEvent.Error(e.message ?: "Unknown error", e))
        } finally {
            close()
        }

        awaitClose {
            Timber.d("OpenAI: Streaming flow closed")
        }
    }

    override suspend fun generateJson(
        prompt: String,
        jsonSchema: String?
    ): Result<String> = runCatching {
        if (apiKey == null) {
            throw AIEngineError.ModelNotLoaded
        }

        Timber.d("OpenAI: Generating JSON (${prompt.length} chars, schema: ${jsonSchema != null})")

        val systemPrompt = "You are a helpful assistant that ALWAYS responds with valid JSON only. " +
                "Never include explanations, markdown formatting, or any text outside the JSON object."

        val enhancedPrompt = if (jsonSchema != null) {
            "$prompt\n\nRespond with a valid JSON object matching this structure:\n$jsonSchema\n\n" +
                    "IMPORTANT: Output ONLY the JSON object, no markdown, no explanations."
        } else {
            "$prompt\n\nRespond with ONLY valid JSON, no markdown or explanations."
        }

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = enhancedPrompt)
        )

        val request = ChatCompletionRequest(
            model = MODEL,
            messages = messages,
            temperature = 0.2f, // Low temperature for consistent JSON
            maxTokens = 2048,
            stream = false,
            responseFormat = ResponseFormat(type = "json_object") // Enable JSON mode
        )

        try {
            withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val response = openAIService.createChatCompletion(
                        authorization = "Bearer $apiKey",
                        request = request
                    )

                    if (response.choices.isEmpty()) {
                        throw AIEngineError.InvalidResponse("No choices in OpenAI response")
                    }

                    val jsonText = response.choices[0].message.content

                    Timber.i("OpenAI: JSON generation complete (${jsonText.length} chars, ${response.usage.totalTokens} tokens)")

                    jsonText
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "OpenAI: JSON generation failed")
            when {
                e.message?.contains("401") == true -> {
                    throw AIEngineError.GenerationFailed("Invalid API key", e)
                }
                e.message?.contains("429") == true -> {
                    throw AIEngineError.GenerationFailed("Rate limit exceeded", e)
                }
                e.message?.contains("timeout") == true -> {
                    throw AIEngineError.GenerationTimeout
                }
                else -> {
                    throw AIEngineError.GenerationFailed("OpenAI API error: ${e.message}", e)
                }
            }
        }
    }

    override fun cancelGeneration() {
        Timber.d("OpenAI: Cancel generation requested (HTTP request will timeout)")
        // Cannot cancel ongoing HTTP request easily, will timeout
    }

    override suspend fun unloadModel() {
        Timber.d("OpenAI: Unloading (clearing API key from memory)")
        apiKey = null
    }

    override fun isModelLoaded(): Boolean {
        return apiKey != null
    }

    override fun getModelInfo(): ModelInfo? {
        return if (apiKey != null) {
            ModelInfo(
                name = MODEL,
                path = "OpenAI API",
                contextLength = CONTEXT_LENGTH
            )
        } else {
            null
        }
    }
}
