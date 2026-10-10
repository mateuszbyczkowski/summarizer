package com.summarizer.app.data.download

import android.content.Context
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.DownloadFailure
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.NetworkHelper
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads model files in the application scope, so a download keeps going when the user
 * leaves the screen. Partial downloads are kept in a `.tmp` file and resumed with HTTP Range.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val modelRepository: ModelRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    private val activeDownloads = ConcurrentHashMap<String, Job>()

    private class DownloadException(
        message: String,
        val failure: DownloadFailure = DownloadFailure.OTHER
    ) : Exception(message)

    /** Start or resume downloading [model]; a running download for the same model is left alone. */
    fun startDownload(model: AIModel) {
        val previous = activeDownloads[model.id]
        if (previous != null && previous.isActive && !previous.isCancelled) return
        // Show the download as running right away (keeping its progress), not once it connects
        _downloadStates.update { states ->
            val current = states[model.id]?.copy(status = DownloadStatus.DOWNLOADING, error = null, failure = null)
            states + (model.id to (current ?: ModelDownloadState(model.id, DownloadStatus.DOWNLOADING)))
        }
        val job = applicationScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            // A paused transfer may still be finishing a write; never let two jobs share the file
            previous?.join()
            try {
                val file = download(model)
                modelRepository.markAsDownloaded(model.id, file.absolutePath, System.currentTimeMillis())
                updateState(model.id, DownloadStatus.COMPLETED, progress = 1f)
                Timber.i("Model ${model.id} downloaded")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Download failed for model ${model.id}")
                val message = when (e) {
                    is DownloadException -> e.message
                    is java.io.IOException -> "Network error: could not reach the download server. Check your connection and try again."
                    else -> e.message
                }
                val failure = when (e) {
                    is DownloadException -> e.failure
                    is java.io.IOException -> DownloadFailure.NETWORK
                    else -> DownloadFailure.OTHER
                }
                updateState(model.id, DownloadStatus.FAILED, error = message ?: "Download failed", failure = failure)
            } finally {
                activeDownloads.remove(model.id, coroutineContext.job)
            }
        }
        activeDownloads[model.id] = job
        job.start()
    }

    /** Stop the transfer but keep the partial file so [startDownload] can resume it. */
    fun pauseDownload(modelId: String) {
        activeDownloads.remove(modelId)?.cancel()
        _downloadStates.update { states ->
            val current = states[modelId] ?: return@update states
            states + (modelId to current.copy(status = DownloadStatus.PAUSED))
        }
    }

    /** Stop the transfer and discard the partial file. */
    fun cancelDownload(modelId: String) {
        applicationScope.launch(Dispatchers.IO) {
            activeDownloads.remove(modelId)?.let { job ->
                job.cancel()
                job.join()
            }
            StorageHelper.StorageLocation.entries.forEach { location ->
                File(StorageHelper.getModelStorageDirectory(context, location), "$modelId.tmp").delete()
            }
            updateState(modelId, DownloadStatus.NOT_STARTED)
        }
    }

    private suspend fun download(model: AIModel): File = withContext(Dispatchers.IO) {
        if (preferencesRepository.isWiFiOnlyDownload() && !NetworkHelper.isWiFiConnected(context)) {
            throw DownloadException(
                "WiFi connection required. Enable mobile data download in settings.",
                DownloadFailure.WIFI_REQUIRED
            )
        }

        val storageLocation = preferencesRepository.getStorageLocation()
        val storageInfo = when (storageLocation) {
            StorageHelper.StorageLocation.INTERNAL -> StorageHelper.getInternalStorageInfo(context)
            StorageHelper.StorageLocation.EXTERNAL -> StorageHelper.getExternalStorageInfo(context)
                ?: StorageHelper.getInternalStorageInfo(context)
        }

        val downloadDir = StorageHelper.getModelStorageDirectory(context, storageLocation)
        // .gguf runs on llama.cpp, .litertlm on LiteRT-LM; the engine is chosen by extension
        val extension = model.downloadUrl.substringAfterLast('/').substringAfterLast('.', "gguf")
        val tempFile = File(downloadDir, "${model.id}.tmp")
        val finalFile = File(downloadDir, "${model.id}.$extension")

        if (finalFile.exists() && finalFile.length() > 0) {
            if (model.checksum == null || sha256(finalFile).equals(model.checksum, ignoreCase = true)) {
                return@withContext finalFile
            }
            finalFile.delete()
        }

        val partialMB = tempFile.length() / (1024 * 1024)
        if (!StorageHelper.hasEnoughSpace(model.sizeInMB - partialMB, storageInfo.availableSpaceMB)) {
            throw DownloadException(
                "Insufficient storage: ${storageInfo.availableSpaceMB}MB available, ${model.sizeInMB}MB required",
                DownloadFailure.NO_SPACE
            )
        }

        updateState(model.id, DownloadStatus.DOWNLOADING, progress = 0f)
        transfer(model, tempFile, allowRestart = true)
        verifyChecksum(model, tempFile)
        moveIntoPlace(tempFile, finalFile)
        finalFile
    }

    private suspend fun transfer(model: AIModel, tempFile: File, allowRestart: Boolean) {
        val startByte = if (tempFile.exists()) tempFile.length() else 0L
        val request = Request.Builder()
            .url(model.downloadUrl)
            .apply { if (startByte > 0) header("Range", "bytes=$startByte-") }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            when (response.code) {
                // Range not satisfiable: the partial file is either complete or invalid
                416 -> {
                    val total = response.totalSizeFromContentRange()
                    if (total != null && total == startByte) return
                    tempFile.delete()
                    if (allowRestart) return transfer(model, tempFile, allowRestart = false)
                    throw DownloadException("Download could not be resumed")
                }
                200, 206 -> Unit
                else -> throw DownloadException("Download failed: HTTP ${response.code}")
            }

            // A 200 means the server ignored the Range header and sends the whole file again
            val resumed = response.code == 206
            val offset = if (resumed) startByte else 0L
            val totalBytes = response.totalSizeFromContentRange()
                ?: response.body.contentLength().takeIf { it > 0 }?.plus(offset)
                ?: 0L

            var downloadedBytes = offset
            var lastProgress = 0f
            response.body.byteStream().use { input ->
                FileOutputStream(tempFile, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        if (progress - lastProgress >= 0.01f) {
                            updateState(model.id, DownloadStatus.DOWNLOADING, progress, downloadedBytes, totalBytes)
                            lastProgress = progress
                        }
                    }
                }
            }

            if (totalBytes > 0 && downloadedBytes != totalBytes) {
                throw DownloadException("Incomplete download: $downloadedBytes of $totalBytes bytes")
            }
        }
    }

    private fun verifyChecksum(model: AIModel, file: File) {
        val expected = model.checksum ?: return
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            throw DownloadException("Checksum verification failed")
        }
    }

    private fun moveIntoPlace(tempFile: File, finalFile: File) {
        Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Parses the total size from "Content-Range: bytes 100-199/2000" or "bytes * /2000". */
    private fun Response.totalSizeFromContentRange(): Long? =
        header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()

    private fun updateState(
        modelId: String,
        status: DownloadStatus,
        progress: Float = 0f,
        downloadedBytes: Long = 0,
        totalBytes: Long = 0,
        error: String? = null,
        failure: DownloadFailure? = null
    ) {
        _downloadStates.update { states ->
            states + (modelId to ModelDownloadState(modelId, status, progress, downloadedBytes, totalBytes, error, failure))
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
