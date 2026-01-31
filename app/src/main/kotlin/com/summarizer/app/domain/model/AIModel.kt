package com.summarizer.app.domain.model

data class AIModel(
    val id: String,
    val name: String,
    val description: String,
    val sizeInMB: Long,
    val downloadUrl: String,
    val isDownloaded: Boolean = false,
    val isRecommended: Boolean = false,
    val minimumRAM: Int = 4, // GB
    val estimatedSpeed: String = "Medium" // Fast, Medium, Slow
)

enum class DownloadStatus {
    NOT_STARTED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class ModelDownloadState(
    val modelId: String,
    val status: DownloadStatus = DownloadStatus.NOT_STARTED,
    val progress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
)
