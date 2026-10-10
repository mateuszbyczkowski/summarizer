package com.summarizer.app.data.download

import android.content.Context
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.model.ModelRecommendation
import com.summarizer.app.domain.model.ModelSetupState
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.DeviceMemory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device model onboarding sets up: picked by the phone's RAM, downloaded in the
 * background by [ModelDownloadManager] while the user is already in the app, and made the
 * active model once it is there. Home shows [state] as a progress card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ModelSetupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val preferencesRepository: PreferencesRepository,
    private val downloadManager: ModelDownloadManager,
    private val catalogSync: ModelCatalogSync,
    private val aiEngine: AIEngine,
    @ApplicationScope private val scope: CoroutineScope
) {
    private data class Tracked(val modelId: String, val model: AIModel?, val download: ModelDownloadState?)

    private val tracked: Flow<Tracked?> = preferencesRepository.observeSetupModelId().flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(
                modelRepository.getAllModels().map { models -> models.firstOrNull { it.id == id } },
                downloadManager.downloadStates.map { it[id] }
            ) { model, download -> Tracked(id, model, download) }
        }
    }

    /** The download Home shows, or null when there is none. */
    val state: Flow<ModelSetupState?> = tracked
        .map { it?.model?.let { model -> ModelSetupState.of(model, it.download) } }
        .distinctUntilChanged()

    init {
        // Finish the setup whichever screen the download was started or cancelled from
        scope.launch {
            tracked.collect { tracked ->
                if (tracked == null) return@collect
                val model = tracked.model
                when {
                    model == null -> forget()
                    model.isDownloaded -> {
                        activate(model)
                        forget()
                    }
                    tracked.download?.status == DownloadStatus.NOT_STARTED -> forget()
                }
            }
        }
    }

    /** The model onboarding would download on this phone, or null when none fits its RAM. */
    fun recommendedModel(): AIModel? =
        ModelRecommendation.pick(ModelCatalog.DEFAULT_MODELS, DeviceMemory.totalRamGb(context))

    /** Starts downloading the recommended model; keeps running after the caller is gone. */
    fun start() {
        scope.launch {
            // The model rows may not exist yet: the model manager has never been opened
            catalogSync.reconcile()
            val ramGb = DeviceMemory.totalRamGb(context)
            val model = ModelRecommendation.pick(modelRepository.getAllModels().first(), ramGb)
            if (model == null) {
                Timber.i("No on-device model fits ${ramGb} GB RAM; leaving the choice to the model manager")
                return@launch
            }
            Timber.i("Setting up ${model.id} for $ramGb GB RAM")
            val path = model.localFilePath
            if (model.isDownloaded && path != null) {
                modelRepository.markAsDownloaded(model.id, path, System.currentTimeMillis())
                activate(model)
                return@launch
            }
            // The download state shows "downloading" right away, so the card never starts stopped
            downloadManager.startDownload(model)
            preferencesRepository.setSetupModelId(model.id)
        }
    }

    /** Resumes (or restarts) a stopped download. */
    fun retry() {
        scope.launch {
            val id = preferencesRepository.observeSetupModelId().first() ?: return@launch
            modelRepository.getModelById(id)?.let(downloadManager::startDownload)
        }
    }

    /** Stops the download and drops the card; the user can still pick a model in the model manager. */
    fun cancel() {
        scope.launch {
            val id = preferencesRepository.observeSetupModelId().first() ?: return@launch
            downloadManager.cancelDownload(id)
            forget()
        }
    }

    /**
     * The download marked the model as the most recent one, which makes it active; a model
     * still loaded in memory (e.g. the debug Demo) has to go so the next summary loads it.
     */
    private suspend fun activate(model: AIModel) {
        if (aiEngine.isModelLoaded() && aiEngine.getModelInfo()?.path != model.localFilePath) {
            aiEngine.unloadModel()
        }
        Timber.i("Model ${model.id} is set up")
    }

    private suspend fun forget() = preferencesRepository.setSetupModelId(null)
}
