package com.summarizer.app.data.local.dao

import androidx.room.*
import com.summarizer.app.data.local.entity.AIModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIModelDao {

    @Query("SELECT * FROM ai_models ORDER BY isRecommended DESC, name ASC")
    fun getAllModels(): Flow<List<AIModelEntity>>

    @Query("SELECT * FROM ai_models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): AIModelEntity?

    @Query("SELECT * FROM ai_models WHERE isDownloaded = 1 ORDER BY downloadedTimestamp DESC LIMIT 1")
    suspend fun getDownloadedModel(): AIModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AIModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AIModelEntity>)

    @Query("UPDATE ai_models SET isDownloaded = :isDownloaded, localFilePath = :filePath, downloadedTimestamp = :timestamp WHERE id = :modelId")
    suspend fun updateDownloadStatus(modelId: String, isDownloaded: Boolean, filePath: String?, timestamp: Long?)

    /** Update catalog fields only; download state and the active-model timestamp are kept. */
    @Query("""
        UPDATE ai_models SET name = :name, description = :description, sizeInMB = :sizeInMB,
            downloadUrl = :downloadUrl, isRecommended = :isRecommended, minimumRAM = :minimumRAM,
            estimatedSpeed = :estimatedSpeed, checksum = :checksum
        WHERE id = :id
    """)
    suspend fun updateCatalogFields(
        id: String,
        name: String,
        description: String,
        sizeInMB: Long,
        downloadUrl: String,
        isRecommended: Boolean,
        minimumRAM: Int,
        estimatedSpeed: String,
        checksum: String?
    )

    @Query("DELETE FROM ai_models WHERE id = :modelId")
    suspend fun deleteModel(modelId: String)

    @Query("DELETE FROM ai_models")
    suspend fun deleteAllModels()
}
