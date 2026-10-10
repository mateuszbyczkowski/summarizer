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
        modelRepository.refreshDownloadState()
        val existing = modelRepository.getAllModels().first()
        val catalogIds = DEFAULT_MODELS.map { it.id }.toSet()

        val staleIds = existing.map { it.id }.filterNot { it in catalogIds }
        for (id in staleIds) {
            Timber.i("Removing stale catalog model: $id")
            modelRepository.deleteModelFile(id) // best-effort, ignored if not downloaded
            modelRepository.deleteModel(id)
        }

        // Refresh catalog metadata (URL, checksum, size, text) while keeping download state
        val existingById = existing.associateBy { it.id }
        DEFAULT_MODELS.forEach { catalogModel ->
            val current = existingById[catalogModel.id] ?: return@forEach
            val refreshed = catalogModel.copy(
                isDownloaded = current.isDownloaded,
                localFilePath = current.localFilePath
            )
            if (refreshed != current) modelRepository.updateCatalogMetadata(refreshed)
        }

        val existingIds = existingById.keys
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

    /** Downloads run in the application scope and keep going after leaving this screen. */
    fun downloadModel(model: AIModel) {
        downloadManager.startDownload(model)
    }

    fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun resumeDownload(modelId: String) {
        _models.value.firstOrNull { it.id == modelId }?.let(downloadManager::startDownload)
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

    fun deleteModel(modelId: String) {
        applicationScope.launch {
            // The file may be memory-mapped by the local engine
            aiEngine.unloadModel()
            if (modelRepository.deleteModelFile(modelId)) {
                Timber.i("Model $modelId deleted successfully")
            } else {
                Timber.e("Failed to delete model $modelId")
            }
        }
    }

    companion object {
        // On-device catalog (Oct 2026), chosen from measurements on a mid-range phone
        // (Dimensity 900, LocalModelBenchmark / LiteRtBenchmark on the debug sample chat):
        // - Gemma 4 on LiteRT-LM (GPU): ~160 tok/s prompt reading, ~30-60 s per summary,
        //   correct Polish. Default.
        // - LFM2.5 on llama.cpp (CPU): fallback for phones whose GPU cannot run Gemma; also
        //   used automatically when GPU loading fails.
        // Qwen3.5 / Gemma 3n GGUFs were dropped: 2-6 min per summary with weaker output.
        // URLs are pinned to a commit and checksum-verified after download.
        internal val DEFAULT_MODELS = listOf(
            AIModel(
                id = "gemma-4-e2b-gpu",
                name = "Gemma 4 E2B (GPU)",
                description = "Recommended. Runs on the phone's GPU: fast, accurate summaries in many languages.",
                sizeInMB = 2010,
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it-gpu.litertlm",
                isRecommended = true,
                minimumRAM = 6,
                estimatedSpeed = "Fast",
                checksum = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5"
            ),
            AIModel(
                id = "gemma-4-e4b-gpu",
                name = "Gemma 4 E4B (GPU, Quality)",
                description = "Larger Gemma 4 for phones with 8 GB+ RAM. Best summary quality, somewhat slower.",
                sizeInMB = 2970,
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it-gpu.litertlm",
                isRecommended = false,
                minimumRAM = 8,
                estimatedSpeed = "Medium",
                checksum = "4912bb5a9c30993c51a7711f763212077458529312175df0573a78323a2bb7ff"
            ),
            AIModel(
                id = "lfm2.5-1.2b-instruct",
                name = "LFM2.5 1.2B (CPU)",
                description = "Small model that runs on the CPU. For phones whose GPU cannot run Gemma; simpler summaries.",
                sizeInMB = 730,
                downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/8ed288026e23958ad9dfa92d53ed773a8eee7125/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
                isRecommended = false,
                minimumRAM = 3,
                estimatedSpeed = "Slow",
                checksum = "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5"
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
