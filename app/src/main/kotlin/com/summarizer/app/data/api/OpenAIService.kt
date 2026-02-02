package com.summarizer.app.data.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

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

    /**
     * Create a streaming chat completion (for future use).
     *
     * @param authorization Bearer token (format: "Bearer sk-...")
     * @param request Chat completion request with stream=true
     * @return Streaming response body (Server-Sent Events format)
     */
    @Streaming
    @POST("v1/chat/completions")
    suspend fun createChatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ResponseBody
}
