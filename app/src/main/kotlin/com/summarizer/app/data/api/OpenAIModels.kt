package com.summarizer.app.data.api

import com.google.gson.annotations.SerializedName

/**
 * OpenAI Chat Completion API request model.
 *
 * Used for gpt-4o-mini API calls for WhatsApp message summarization.
 */
data class ChatCompletionRequest(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<ChatMessage>,

    @SerializedName("temperature")
    val temperature: Float = 0.7f,

    @SerializedName("max_tokens")
    val maxTokens: Int = 512,

    @SerializedName("stream")
    val stream: Boolean = false,

    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null
)

/**
 * Chat message in OpenAI API format.
 */
data class ChatMessage(
    @SerializedName("role")
    val role: String, // "system", "user", "assistant"

    @SerializedName("content")
    val content: String
)

/**
 * Response format specification for structured outputs.
 */
data class ResponseFormat(
    @SerializedName("type")
    val type: String // "json_object" for JSON mode
)

/**
 * OpenAI Chat Completion API response model.
 */
data class ChatCompletionResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val objectType: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<ChatChoice>,

    @SerializedName("usage")
    val usage: Usage
)

/**
 * Individual choice in the completion response.
 */
data class ChatChoice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("message")
    val message: ChatMessage,

    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * Token usage statistics for cost tracking.
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * OpenAI API error response.
 */
data class OpenAIErrorResponse(
    @SerializedName("error")
    val error: OpenAIError
)

data class OpenAIError(
    @SerializedName("message")
    val message: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("code")
    val code: String?
)

/**
 * Streaming response chunk (for future use).
 */
data class ChatCompletionChunk(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val objectType: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<ChunkChoice>
)

data class ChunkChoice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("delta")
    val delta: ChatDelta,

    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ChatDelta(
    @SerializedName("role")
    val role: String?,

    @SerializedName("content")
    val content: String?
)
