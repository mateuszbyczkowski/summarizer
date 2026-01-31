package com.summarizer.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.download.ModelDownloadManager
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val preferencesRepository: PreferencesRepository
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
                            id = "tinyllama-1.1b",
                            name = "TinyLlama 1.1B",
                            description = "Small and fast model, perfect for budget devices. Good for basic summaries.",
                            sizeInMB = 700,
                            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                            isRecommended = true,
                            minimumRAM = 4,
                            estimatedSpeed = "Fast"
                        ),
                        AIModel(
                            id = "phi-2-2.7b",
                            name = "Phi-2 2.7B",
                            description = "More capable model with better understanding. Requires more RAM.",
                            sizeInMB = 1800,
                            downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
                            isRecommended = false,
                            minimumRAM = 6,
                            estimatedSpeed = "Medium"
                        ),
                        AIModel(
                            id = "gemma-2b",
                            name = "Gemma 2B",
                            description = "Google's efficient model, balanced performance and speed.",
                            sizeInMB = 1400,
                            downloadUrl = "https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/gemma-2b-it.Q4_K_M.gguf",
                            isRecommended = false,
                            minimumRAM = 4,
                            estimatedSpeed = "Fast"
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
}
