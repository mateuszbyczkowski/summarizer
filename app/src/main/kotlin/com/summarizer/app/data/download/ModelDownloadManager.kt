package com.summarizer.app.data.download

import android.content.Context
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.NetworkHelper
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val okHttpClient: OkHttpClient
) {
    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    private val activeDownloads = mutableMapOf<String, Boolean>() // modelId -> isPaused

    suspend fun downloadModel(
        modelId: String,
        downloadUrl: String,
        expectedSizeMB: Long,
        expectedChecksum: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Check WiFi requirement
            val wifiOnly = preferencesRepository.isWiFiOnlyDownload()
            if (wifiOnly && !NetworkHelper.isWiFiConnected(context)) {
                updateDownloadState(
                    modelId,
                    DownloadStatus.FAILED,
                    error = "WiFi connection required. Enable mobile data download in settings."
                )
                return@withContext Result.failure(Exception("WiFi required"))
            }

            // Get storage location
            val storageLocation = preferencesRepository.getStorageLocation()
            val storageInfo = when (storageLocation) {
                StorageHelper.StorageLocation.INTERNAL -> StorageHelper.getInternalStorageInfo(context)
                StorageHelper.StorageLocation.EXTERNAL -> StorageHelper.getExternalStorageInfo(context)
                    ?: StorageHelper.getInternalStorageInfo(context)
            }

            // Check storage space
            if (!StorageHelper.hasEnoughSpace(expectedSizeMB, storageInfo.availableSpaceMB)) {
                updateDownloadState(
                    modelId,
                    DownloadStatus.FAILED,
                    error = "Insufficient storage space"
                )
                return@withContext Result.failure(Exception("Insufficient storage"))
            }

            // Create download directory
            val downloadDir = StorageHelper.getModelStorageDirectory(context, storageLocation)
            val tempFile = File(downloadDir, "$modelId.tmp")
            val finalFile = File(downloadDir, "$modelId.gguf")

            // Check if already downloaded
            if (finalFile.exists() && finalFile.length() > 0) {
                Timber.d("Model $modelId already downloaded")
                updateDownloadState(modelId, DownloadStatus.COMPLETED, progress = 1f)
                return@withContext Result.success(finalFile)
            }

            // Initialize download state
            activeDownloads[modelId] = false
            updateDownloadState(modelId, DownloadStatus.DOWNLOADING, progress = 0f)

            // Check for partial download
            val startByte = if (tempFile.exists()) tempFile.length() else 0L

            // Build request with range header for resume support
            val request = Request.Builder()
                .url(downloadUrl)
                .apply {
                    if (startByte > 0) {
                        addHeader("Range", "bytes=$startByte-")
                    }
                }
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                updateDownloadState(
                    modelId,
                    DownloadStatus.FAILED,
                    error = "Download failed: ${response.code}"
                )
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val totalBytes = response.header("Content-Length")?.toLongOrNull() ?: 0L
            val body = response.body ?: run {
                updateDownloadState(modelId, DownloadStatus.FAILED, error = "Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }

            // Download with progress tracking
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile, startByte > 0)

            val buffer = ByteArray(8192)
            var downloadedBytes = startByte
            var lastProgressUpdate = 0f

            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check if download is paused
                        while (activeDownloads[modelId] == true) {
                            kotlinx.coroutines.delay(100)
                        }

                        // Check if download was cancelled
                        if (!activeDownloads.containsKey(modelId)) {
                            throw Exception("Download cancelled")
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress (throttle updates to every 1%)
                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes
                        } else {
                            0f
                        }

                        if (progress - lastProgressUpdate >= 0.01f || progress >= 1f) {
                            updateDownloadState(
                                modelId,
                                DownloadStatus.DOWNLOADING,
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                            lastProgressUpdate = progress
                        }
                    }
                }
            }

            // Verify checksum if provided
            if (expectedChecksum != null) {
                val actualChecksum = calculateMD5(tempFile)
                if (actualChecksum != expectedChecksum) {
                    tempFile.delete()
                    updateDownloadState(
                        modelId,
                        DownloadStatus.FAILED,
                        error = "Checksum verification failed"
                    )
                    return@withContext Result.failure(Exception("Checksum mismatch"))
                }
            }

            // Move temp file to final location
            tempFile.renameTo(finalFile)
            activeDownloads.remove(modelId)

            updateDownloadState(modelId, DownloadStatus.COMPLETED, progress = 1f)
            Timber.d("Model $modelId downloaded successfully")

            Result.success(finalFile)
        } catch (e: Exception) {
            Timber.e(e, "Download failed for model $modelId")
            activeDownloads.remove(modelId)
            updateDownloadState(
                modelId,
                DownloadStatus.FAILED,
                error = e.message ?: "Download failed"
            )
            Result.failure(e)
        }
    }

    fun pauseDownload(modelId: String) {
        activeDownloads[modelId] = true
        updateDownloadState(modelId, DownloadStatus.PAUSED)
    }

    fun resumeDownload(modelId: String) {
        activeDownloads[modelId] = false
        updateDownloadState(modelId, DownloadStatus.DOWNLOADING)
    }

    fun cancelDownload(modelId: String) {
        activeDownloads.remove(modelId)
        updateDownloadState(modelId, DownloadStatus.NOT_STARTED)
    }

    private fun updateDownloadState(
        modelId: String,
        status: DownloadStatus,
        progress: Float = 0f,
        downloadedBytes: Long = 0,
        totalBytes: Long = 0,
        error: String? = null
    ) {
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[modelId] = ModelDownloadState(
            modelId = modelId,
            status = status,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            error = error
        )
        _downloadStates.value = currentStates
    }

    private fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
