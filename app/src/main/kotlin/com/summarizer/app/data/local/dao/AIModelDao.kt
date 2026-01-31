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

    @Query("SELECT * FROM ai_models WHERE isDownloaded = 1 LIMIT 1")
    suspend fun getDownloadedModel(): AIModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AIModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AIModelEntity>)

    @Update
    suspend fun updateModel(model: AIModelEntity)

    @Query("UPDATE ai_models SET isDownloaded = :isDownloaded, localFilePath = :filePath, downloadedTimestamp = :timestamp WHERE id = :modelId")
    suspend fun updateDownloadStatus(modelId: String, isDownloaded: Boolean, filePath: String?, timestamp: Long?)

    @Query("DELETE FROM ai_models WHERE id = :modelId")
    suspend fun deleteModel(modelId: String)

    @Query("DELETE FROM ai_models")
    suspend fun deleteAllModels()
}
