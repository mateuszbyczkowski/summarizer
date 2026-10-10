package com.summarizer.app.data.ai

import com.summarizer.app.data.api.ChatCompletionRequest
import com.summarizer.app.data.api.ChatMessage
import com.summarizer.app.data.api.OpenAIService
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.RetryHelper
import com.summarizer.app.util.suspendRunCatching
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
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
        private const val TIMEOUT_MS = 30_000L // 30 seconds
        private const val CONTEXT_TOKENS = 128_000
        // Well within the context window; bounds cost per summary
        private const val MAX_PROMPT_CHARS = 60_000

        /** Only network errors, rate limits and server errors are worth retrying. */
        private fun isTransient(error: Throwable): Boolean = when (error) {
            is HttpException -> error.code() == 429 || error.code() >= 500
            is IOException -> true
            is RequestTimeoutException -> true
            else -> false
        }
    }

    override suspend fun loadModel(modelPath: String): Result<Unit> = suspendRunCatching {
        // For OpenAI, "loading" means validating the API key exists
        requireApiKey()
        Timber.i("OpenAI API key validated successfully")
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = suspendRunCatching {
        // Always fetch the latest API key from repository
        val apiKey = requireApiKey()
        val selectedModel = preferencesRepository.getSelectedOpenAIModel()

        Timber.d("OpenAI: Generating text with model: $selectedModel (${prompt.length} chars, max tokens: $maxTokens, temp: $temperature)")

        val messages = buildList {
            if (systemPrompt != null) {
                add(ChatMessage(role = "system", content = systemPrompt))
            }
            add(ChatMessage(role = "user", content = prompt))
        }

        val request = ChatCompletionRequest(
            model = selectedModel,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = false
        )

        RetryHelper.retryResult(
            times = 3,
            initialDelay = 2000,
            maxDelay = 10000,
            shouldRetry = ::isTransient
        ) {
            suspendRunCatching {
                // withTimeoutOrNull: a TimeoutCancellationException would look like caller cancellation
                withTimeoutOrNull(TIMEOUT_MS) {
                    val response = openAIService.createChatCompletion(
                        authorization = "Bearer $apiKey",
                        request = request
                    )
                    val generatedText = response.choices.firstOrNull()?.message?.content
                        ?: throw AIEngineError.InvalidResponse("No choices in OpenAI response")

                    Timber.i("OpenAI: Generation complete (${generatedText.length} chars, ${response.usage.totalTokens} tokens)")
                    generatedText
                } ?: throw RequestTimeoutException()
            }
        }.getOrElse { error ->
            Timber.e(error, "OpenAI: Generation failed")
            throw toEngineError(error)
        }
    }

    private class RequestTimeoutException : Exception("OpenAI request timed out")

    private suspend fun requireApiKey(): String {
        val key = preferencesRepository.getOpenAIApiKey()
        if (key.isNullOrBlank()) {
            throw AIEngineError.ModelLoadFailed("No OpenAI API key configured. Please add your API key in Settings.")
        }
        return key
    }

    private fun toEngineError(error: Throwable): Throwable = when {
        error is AIEngineError -> error
        error is RequestTimeoutException -> AIEngineError.GenerationTimeout
        error is HttpException && error.code() == 401 ->
            AIEngineError.GenerationFailed("Invalid API key. Please check your OpenAI API key in Settings.", error)
        error is HttpException && error.code() == 429 ->
            AIEngineError.GenerationFailed("Rate limit exceeded. Please try again later.", error)
        error is HttpException ->
            AIEngineError.GenerationFailed("OpenAI API error (HTTP ${error.code()})", error)
        else -> AIEngineError.GenerationFailed("OpenAI API error: ${error.message}", error)
    }

    override fun cancelGeneration() {
        // HTTP calls are cancelled through coroutine cancellation of the caller
    }

    override suspend fun unloadModel() {
        // Nothing to unload; the API key is only cleared by an explicit action in Settings
    }

    override suspend fun maxPromptChars(): Int = MAX_PROMPT_CHARS

    override suspend fun isModelLoaded(): Boolean =
        !preferencesRepository.getOpenAIApiKey().isNullOrBlank()

    override suspend fun getModelInfo(): ModelInfo? {
        if (!isModelLoaded()) return null
        return ModelInfo(
            name = preferencesRepository.getSelectedOpenAIModel(),
            path = "OpenAI API",
            contextLength = CONTEXT_TOKENS
        )
    }
}
