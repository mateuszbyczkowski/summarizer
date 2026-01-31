package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.AIModel
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun getAllModels(): Flow<List<AIModel>>
    suspend fun getModelById(modelId: String): AIModel?
    suspend fun getDownloadedModel(): AIModel?
    suspend fun insertModel(model: AIModel)
    suspend fun insertModels(models: List<AIModel>)
    suspend fun updateModel(model: AIModel)
    suspend fun markAsDownloaded(modelId: String, filePath: String, timestamp: Long)
    suspend fun deleteModel(modelId: String)
    suspend fun deleteModelFile(modelId: String): Boolean
}
