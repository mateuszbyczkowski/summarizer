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
import kotlinx.coroutines.flow.first
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
            reconcileCatalog()
            modelRepository.getAllModels().collect { models ->
                _models.value = models
            }
        }
    }

    /**
     * Bring the seeded catalog up to date with [DEFAULT_MODELS]:
     *  - Insert/overwrite the current catalog rows (OnConflictStrategy.REPLACE preserves nothing
     *    other than what we re-supply; downloaded flags on these IDs stay correct because we never
     *    re-seed an existing ID — see check below).
     *  - Delete any previously seeded rows whose IDs are no longer in the catalog, so users
     *    upgrading from a prior model list (Qwen3 / Phi-4-mini, earlier Qwen2.5 / Phi-3 / Llama-3.2) don't see stale
     *    entries. Downloaded files for retired IDs are wiped via deleteModelFile().
     */
    private suspend fun reconcileCatalog() {
        val existing = modelRepository.getAllModels().first()
        val catalogIds = DEFAULT_MODELS.map { it.id }.toSet()

        val staleIds = existing.map { it.id }.filterNot { it in catalogIds }
        for (id in staleIds) {
            Timber.i("Removing stale catalog model: $id")
            modelRepository.deleteModelFile(id) // best-effort, ignored if not downloaded
            modelRepository.deleteModel(id)
        }

        val existingIds = existing.map { it.id }.toSet()
        val toInsert = DEFAULT_MODELS.filterNot { it.id in existingIds }
        if (toInsert.isNotEmpty()) {
            Timber.i("Seeding catalog with ${toInsert.size} new model(s): ${toInsert.map { it.id }}")
            modelRepository.insertModels(toInsert)
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

    companion object {
        // On-device catalog (Sep 2026). Qwen3.5 small series (Apache 2.0, 256K native context,
        // reasoning off by default on <=4B) plus LFM2.5 as the lowest-RAM option.
        // Q4_K_M quantization across the board. Text-only GGUFs; the Qwen3.5 vision projector
        // (mmproj) is a separate file we don't download. Sizes are approximate GGUF artifact sizes.
        // Requires a llama.cpp build with qwen35 / lfm2 architecture support (Llamatik 1.x).
        internal val DEFAULT_MODELS = listOf(
            AIModel(
                id = "qwen3.5-0.8b",
                name = "Qwen3.5 0.8B (Fast)",
                description = "Fastest option. Best for low-end devices and quick previews. 256K context, 201 languages.",
                sizeInMB = 530,
                downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
                isRecommended = false,
                minimumRAM = 3,
                estimatedSpeed = "Fast"
            ),
            AIModel(
                id = "lfm2.5-1.2b-instruct",
                name = "LFM2.5 1.2B (Efficient)",
                description = "Liquid AI hybrid model. Runs in under 1GB RAM with very fast decoding — ideal for older phones. ~120K context.",
                sizeInMB = 730,
                downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
                isRecommended = false,
                minimumRAM = 3,
                estimatedSpeed = "Fast"
            ),
            AIModel(
                id = "qwen3.5-2b",
                name = "Qwen3.5 2B (Balanced)",
                description = "Recommended daily driver. Strong multilingual summaries with snappy on-device speed. 256K context.",
                sizeInMB = 1300,
                downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
                isRecommended = true,
                minimumRAM = 4,
                estimatedSpeed = "Medium"
            ),
            AIModel(
                id = "qwen3.5-4b",
                name = "Qwen3.5 4B (Quality)",
                description = "Best summary quality on flagship phones. Outperforms Qwen3 4B 2507 by a wide margin. 256K context.",
                sizeInMB = 2700,
                downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
                isRecommended = false,
                minimumRAM = 6,
                estimatedSpeed = "Slow"
            )
        )
    }

    /**
     * Pre-load the selected model and mark it active. Active = most recent downloadedTimestamp,
     * so re-marking here is how the user switches between multiple downloaded models.
     */
    fun preloadModel(model: AIModel) {
        if (model.localFilePath == null) {
            Timber.w("Cannot preload model - no local file path")
            return
        }

        applicationScope.launch {
            modelRepository.markAsDownloaded(
                modelId = model.id,
                filePath = model.localFilePath,
                timestamp = System.currentTimeMillis()
            )

            // Unload any previously loaded model so the new one takes effect immediately.
            if (aiEngine.isModelLoaded() &&
                aiEngine.getModelInfo()?.path != model.localFilePath
            ) {
                Timber.i("Unloading previous model before loading ${model.name}")
                aiEngine.unloadModel()
            }

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
