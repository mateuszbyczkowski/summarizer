package com.summarizer.app.domain.ai

/**
 * Interface for AI inference engine operations.
 * Abstracts the underlying LLM implementation (Llamatik, llama.cpp, etc.)
 */
interface AIEngine {

    /**
     * Load an AI model from the specified file path.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @return Result indicating success or failure with error message
     */
    suspend fun loadModel(modelPath: String): Result<Unit>

    /**
     * Generate text based on the provided prompt.
     *
     * @param prompt The input prompt for text generation
     * @param systemPrompt Optional system-level instructions (default: null)
     * @param maxTokens Maximum number of tokens to generate (default: 512)
     * @param temperature Sampling temperature 0.0-1.0 (default: 0.7)
     * @return Result containing generated text or error
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Result<String>

    /**
     * Cancel ongoing text generation.
     */
    fun cancelGeneration()

    /**
     * Unload the current model and free resources.
     */
    suspend fun unloadModel()

    /**
     * Check if the engine is ready for inference (local: model loaded; cloud: API key configured).
     *
     * @return true if model is loaded and ready for inference
     */
    suspend fun isModelLoaded(): Boolean

    /**
     * Get information about the currently loaded model.
     *
     * @return ModelInfo or null if no model is loaded
     */
    suspend fun getModelInfo(): ModelInfo?
}

/**
 * Information about a loaded AI model.
 */
data class ModelInfo(
    val name: String,
    val path: String,
    val contextLength: Int,
    val loadedAt: Long = System.currentTimeMillis()
)

/**
 * Common errors that can occur during AI operations.
 */
sealed class AIEngineError : Exception() {
    data class ModelNotFound(val path: String) : AIEngineError() {
        override val message: String = "Model file not found at: $path"
    }

    data class ModelLoadFailed(override val message: String, override val cause: Throwable? = null) : AIEngineError()

    data class GenerationFailed(override val message: String, override val cause: Throwable? = null) : AIEngineError()

    data object ModelNotLoaded : AIEngineError() {
        override val message: String = "No model is currently loaded"
    }

    data class InvalidResponse(val response: String) : AIEngineError() {
        override val message: String = "Invalid or malformed response from model"
    }

    data object GenerationTimeout : AIEngineError() {
        override val message: String = "Generation exceeded maximum timeout"
    }

    data class OutOfMemory(override val message: String) : AIEngineError()
}
