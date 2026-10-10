package com.summarizer.app.ui.screens.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.BuildConfig
import com.summarizer.app.data.debug.DebugSampleData
import com.summarizer.app.data.download.ModelCatalogSync
import com.summarizer.app.data.download.ModelSetupManager
import com.summarizer.app.data.download.ModelDownloadManager
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import com.summarizer.app.util.StorageHelper
import com.summarizer.app.util.ThreadNaming
import com.summarizer.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val downloadManager: ModelDownloadManager,
    private val catalogSync: ModelCatalogSync,
    private val modelSetupManager: ModelSetupManager,
    private val preferencesRepository: PreferencesRepository,
    private val workScheduler: WorkScheduler,
    private val aiEngine: AIEngine,
    threadRepository: ThreadRepository,
    private val generationCoordinator: SummaryGenerationCoordinator,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _models = MutableStateFlow<List<AIModel>>(emptyList())
    val models: StateFlow<List<AIModel>> = _models.asStateFlow()

    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = downloadManager.downloadStates

    private val _isWiFiOnly = MutableStateFlow(true)
    val isWiFiOnly: StateFlow<Boolean> = _isWiFiOnly.asStateFlow()

    /** Bumped when the active model changes without changing the model list (see [preloadModel]). */
    private val activeModelChanges = MutableStateFlow(0)

    /** The active on-device model (most recently selected downloaded one), or null. */
    val activeModelId: StateFlow<String?> = combine(_models, activeModelChanges) { models, _ -> models }
        .mapLatest { modelRepository.getDownloadedModel()?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The debug sample chat ("Try on example"), when it exists: debug builds seed it at start.
     * Release builds never look it up.
     */
    val sampleThreadId: StateFlow<String?> =
        (if (BuildConfig.DEBUG) {
            threadRepository.observeThread(ThreadNaming.threadIdFor(DebugSampleData.THREAD_NAME)).map { it?.threadId }
        } else {
            flowOf(null)
        }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    /** Space used by models, by everything else and free, where new models are saved. */
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    init {
        loadModels()
        loadWiFiPreference()
    }

    private fun loadModels() {
        viewModelScope.launch {
            catalogSync.reconcile()
            modelRepository.getAllModels().collect { models ->
                _models.value = models
                // A download finished or a model was deleted
                refreshStorage()
            }
        }
    }

    /** Re-reads the storage location and usage, e.g. when the screen resumes. */
    fun refreshStorage() {
        viewModelScope.launch {
            val location = preferencesRepository.getStorageLocation()
            _storage.value = withContext(Dispatchers.IO) { readStorageUsage(location) }
        }
    }

    private fun readStorageUsage(location: StorageHelper.StorageLocation): StorageUsage {
        val external = StorageHelper.getExternalStorageInfo(context)
        // Downloads fall back to internal storage when the SD card is gone
        val info = if (location == StorageHelper.StorageLocation.EXTERNAL && external != null) {
            external
        } else {
            StorageHelper.getInternalStorageInfo(context)
        }
        val modelsBytes = StorageHelper.getModelStorageDirectory(context, info.location)
            .listFiles()
            ?.sumOf { it.length() }
            ?: 0L
        return StorageUsage(
            location = info.location,
            totalMB = info.totalSpaceMB,
            freeMB = info.availableSpaceMB,
            modelsMB = modelsBytes / (1024 * 1024),
            externalAvailable = external != null
        )
    }

    /** Summarizes the debug sample chat with the active engine; the result arrives as a notification. */
    fun tryOnExample(): String? {
        val threadId = sampleThreadId.value ?: return null
        generationCoordinator.generate(threadId)
        return threadId
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

    /**
     * Pre-load the selected model and mark it active. Active = most recent downloadedTimestamp,
     * so re-marking here is how the user switches between multiple downloaded models.
     *
     * @param onActivated called on the main thread once the model is active and the engine is
     *   switched to on-device, before the model has loaded
     */
    fun preloadModel(model: AIModel, onActivated: () -> Unit = {}) {
        if (model.localFilePath == null) {
            Timber.w("Cannot preload model - no local file path")
            return
        }

        applicationScope.launch {
            val alreadyActive = modelRepository.getDownloadedModel()?.id == model.id
            modelRepository.markAsDownloaded(
                modelId = model.id,
                filePath = model.localFilePath,
                timestamp = System.currentTimeMillis()
            )
            // A newly chosen model must not be taken over by the onboarding download; re-using
            // the active one changes nothing, so that download keeps its progress
            if (!alreadyActive) modelSetupManager.onModelChosen(model.id)
            // Choosing an on-device model means using the on-device engine
            if (preferencesRepository.getAIProvider() != AIProvider.LOCAL) {
                preferencesRepository.setAIProvider(AIProvider.LOCAL)
                workScheduler.refreshAutoSummarization()
            }
            // Re-marking only changes the timestamp, which the model list does not show
            activeModelChanges.update { it + 1 }
            withContext(Dispatchers.Main) { onActivated() }

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
