package com.summarizer.app.data.repository

import com.summarizer.app.data.local.dao.AIModelDao
import com.summarizer.app.data.local.entity.AIModelEntity
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    private val aiModelDao: AIModelDao
) : ModelRepository {

    override fun getAllModels(): Flow<List<AIModel>> {
        return aiModelDao.getAllModels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getModelById(modelId: String): AIModel? {
        return aiModelDao.getModelById(modelId)?.toDomainModel()
    }

    override suspend fun getDownloadedModel(): AIModel? {
        return aiModelDao.getDownloadedModel()?.toDomainModel()
    }

    override suspend fun insertModel(model: AIModel) {
        aiModelDao.insertModel(model.toEntity())
    }

    override suspend fun insertModels(models: List<AIModel>) {
        aiModelDao.insertModels(models.map { it.toEntity() })
    }

    override suspend fun updateModel(model: AIModel) {
        aiModelDao.updateModel(model.toEntity())
    }

    override suspend fun markAsDownloaded(modelId: String, filePath: String, timestamp: Long) {
        aiModelDao.updateDownloadStatus(modelId, true, filePath, timestamp)
    }

    override suspend fun deleteModel(modelId: String) {
        aiModelDao.deleteModel(modelId)
    }

    override suspend fun deleteModelFile(modelId: String): Boolean {
        val model = aiModelDao.getModelById(modelId)
        return if (model?.localFilePath != null) {
            val file = File(model.localFilePath)
            val deleted = file.exists() && file.delete()
            if (deleted) {
                aiModelDao.updateDownloadStatus(modelId, false, null, null)
            }
            deleted
        } else {
            false
        }
    }

    private fun AIModelEntity.toDomainModel(): AIModel {
        return AIModel(
            id = id,
            name = name,
            description = description,
            sizeInMB = sizeInMB,
            downloadUrl = downloadUrl,
            isDownloaded = isDownloaded,
            isRecommended = isRecommended,
            minimumRAM = minimumRAM,
            estimatedSpeed = estimatedSpeed,
            localFilePath = localFilePath,
            checksum = checksum
        )
    }

    private fun AIModel.toEntity(): AIModelEntity {
        return AIModelEntity(
            id = id,
            name = name,
            description = description,
            sizeInMB = sizeInMB,
            downloadUrl = downloadUrl,
            isDownloaded = isDownloaded,
            isRecommended = isRecommended,
            minimumRAM = minimumRAM,
            estimatedSpeed = estimatedSpeed,
            localFilePath = localFilePath,
            checksum = checksum
        )
    }
}
