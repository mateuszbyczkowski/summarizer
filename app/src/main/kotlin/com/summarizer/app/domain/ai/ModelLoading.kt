package com.summarizer.app.domain.ai

import com.summarizer.app.domain.repository.ModelRepository

/**
 * Make sure the active engine is ready, loading the most recently selected local model if needed.
 * For the OpenAI provider this only validates that an API key is configured.
 */
suspend fun AIEngine.ensureModelLoaded(modelRepository: ModelRepository): Result<Unit> {
    if (isModelLoaded()) return Result.success(Unit)
    val modelPath = modelRepository.getDownloadedModel()?.localFilePath.orEmpty()
    return loadModel(modelPath)
}
