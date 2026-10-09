package com.summarizer.app.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for OpenAI Chat Completions API.
 *
 * Base URL: https://api.openai.com/
 * Documentation: https://platform.openai.com/docs/api-reference/chat
 */
interface OpenAIService {

    /**
     * Create a chat completion with OpenAI API.
     *
     * @param authorization Bearer token (format: "Bearer sk-...")
     * @param request Chat completion request with messages and parameters
     * @return Chat completion response with generated text
     */
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

}
