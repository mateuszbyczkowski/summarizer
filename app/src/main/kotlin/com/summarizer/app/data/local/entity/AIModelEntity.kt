package com.summarizer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_models")
data class AIModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val sizeInMB: Long,
    val downloadUrl: String,
    val isDownloaded: Boolean = false,
    val isRecommended: Boolean = false,
    val minimumRAM: Int = 4,
    val estimatedSpeed: String = "Medium",
    val localFilePath: String? = null,
    val checksum: String? = null,
    val downloadedTimestamp: Long? = null
)
