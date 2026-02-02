package com.summarizer.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.download.ModelDownloadManager
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val downloadManager: ModelDownloadManager,
    private val preferencesRepository: PreferencesRepository,
    private val aiEngine: AIEngine,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _models = MutableStateFlow<List<AIModel>>(emptyList())
    val models: StateFlow<List<AIModel>> = _models.asStateFlow()

    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = downloadManager.downloadStates

    private val _isWiFiOnly = MutableStateFlow(true)
    val isWiFiOnly: StateFlow<Boolean> = _isWiFiOnly.asStateFlow()

    init {
        loadModels()
        loadWiFiPreference()
    }

    private fun loadModels() {
        viewModelScope.launch {
            // For now, insert sample models if none exist
            val existingModels = modelRepository.getAllModels()
            existingModels.collect { models ->
                if (models.isEmpty()) {
                    // Insert default models
                    val defaultModels = listOf(
                        AIModel(
                            id = "qwen2.5-3b",
                            name = "Qwen2.5 3B",
                            description = "Alibaba's latest model with excellent understanding and summarization. Great balance of quality and speed.",
                            sizeInMB = 1900,
                            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                            isRecommended = true,
                            minimumRAM = 6,
                            estimatedSpeed = "Medium"
                        ),
                        AIModel(
                            id = "phi-3-mini-3.8b",
                            name = "Phi-3 Mini 3.8B",
                            description = "Microsoft's newest Phi model with significantly improved instruction following and reasoning.",
                            sizeInMB = 2300,
                            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
                            isRecommended = false,
                            minimumRAM = 6,
                            estimatedSpeed = "Medium"
                        ),
                        AIModel(
                            id = "llama-3.2-3b",
                            name = "Llama 3.2 3B",
                            description = "Meta's latest compact model with strong performance on conversation understanding.",
                            sizeInMB = 2000,
                            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                            isRecommended = false,
                            minimumRAM = 6,
                            estimatedSpeed = "Medium"
                        )
                    )
                    modelRepository.insertModels(defaultModels)
                }
                _models.value = models
            }
        }
    }

    private fun loadWiFiPreference() {
        viewModelScope.launch {
            _isWiFiOnly.value = preferencesRepository.isWiFiOnlyDownload()
        }
    }

    fun downloadModel(model: AIModel) {
        viewModelScope.launch {
            Timber.d("Starting download for model: ${model.id}")
            val result = downloadManager.downloadModel(
                modelId = model.id,
                downloadUrl = model.downloadUrl,
                expectedSizeMB = model.sizeInMB,
                expectedChecksum = null // TODO: Add checksums to model metadata
            )

            result.onSuccess { file ->
                Timber.d("Model ${model.id} downloaded successfully to ${file.absolutePath}")
                modelRepository.markAsDownloaded(
                    modelId = model.id,
                    filePath = file.absolutePath,
                    timestamp = System.currentTimeMillis()
                )
            }.onFailure { error ->
                Timber.e(error, "Failed to download model ${model.id}")
            }
        }
    }

    fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun resumeDownload(modelId: String) {
        downloadManager.resumeDownload(modelId)
    }

    fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    fun toggleWiFiOnly() {
        viewModelScope.launch {
            val newValue = !_isWiFiOnly.value
            _isWiFiOnly.value = newValue
            preferencesRepository.setWiFiOnlyDownload(newValue)
        }
    }

    fun setStorageLocation(location: StorageHelper.StorageLocation) {
        viewModelScope.launch {
            preferencesRepository.setStorageLocation(location)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            Timber.d("Deleting model: $modelId")
            val success = modelRepository.deleteModelFile(modelId)
            if (success) {
                Timber.i("Model $modelId deleted successfully")
                // Unload model if it was loaded
                if (aiEngine.isModelLoaded()) {
                    aiEngine.unloadModel()
                }
            } else {
                Timber.e("Failed to delete model $modelId")
            }
        }
    }

    /**
     * Pre-load the model in the background to make summarization faster.
     * Called when user clicks "Use This Model".
     */
    fun preloadModel(model: AIModel) {
        if (model.localFilePath == null) {
            Timber.w("Cannot preload model - no local file path")
            return
        }

        // Launch in application scope so it survives screen navigation
        applicationScope.launch {
            try {
                Timber.i("Pre-loading model in background: ${model.name}")
                aiEngine.loadModel(model.localFilePath).getOrThrow()
                Timber.i("Model pre-loaded successfully: ${model.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to pre-load model: ${model.name}")
            }
        }
    }
}
