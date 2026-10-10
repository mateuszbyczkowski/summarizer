package com.summarizer.app.data.download

import android.content.Context
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.ModelGate
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.domain.model.ModelRecommendation
import com.summarizer.app.domain.model.ModelSetupState
import com.summarizer.app.domain.model.SetupStopReason
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.DeviceMemory
import com.summarizer.app.util.NetworkHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
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
 *
 * Also sets up the small CPU model when the phone's GPU cannot run the chosen one
 * ([startCpuFallback]), resumes a download that waits for Wi-Fi once Wi-Fi connects, and steps
 * aside when the user picks a model themselves ([onModelChosen]).
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
    private val modelGate: ModelGate,
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
        // "Wi-Fi only" stopped the download: carry on by itself once Wi-Fi is back
        scope.launch {
            state
                .map { (it as? ModelSetupState.Stopped)?.reason == SetupStopReason.WIFI_REQUIRED }
                .distinctUntilChanged()
                .collectLatest { waiting ->
                    if (!waiting) return@collectLatest
                    // The same condition the download checks, so a retry cannot fail again at once
                    NetworkHelper.defaultWifiConnections(context).first()
                    Timber.i("Wi-Fi connected; resuming the model setup download")
                    retry()
                }
        }
    }

    /** The model onboarding would download on this phone, or null when none fits its RAM. */
    fun recommendedModel(): AIModel? =
        ModelRecommendation.pick(ModelCatalog.DEFAULT_MODELS, DeviceMemory.totalRamGb(context))

    /** Starts downloading the recommended model; keeps running after the caller is gone. */
    fun start() = setUp(ModelRecommendation::pick) { fallbackModelId = null }

    /**
     * The GPU could not load the active model and no CPU model is downloaded: set up the small
     * CPU model, which then becomes active, so summaries work again without the user's help.
     */
    fun startCpuFallback() {
        scope.launch {
            // Cancelled once by the user: do not download it again behind their back
            if (preferencesRepository.isCpuFallbackDeclined()) return@launch
            setUp(ModelRecommendation::cpuFallback) { fallbackModelId = it }
        }
    }

    /** The model a running [startCpuFallback] downloads, so its cancel can be remembered. */
    @Volatile
    private var fallbackModelId: String? = null

    /**
     * The user made a model active in the model manager: their choice wins, so the automatic
     * download stops instead of taking over (and unloading their model) when it finishes.
     */
    fun onModelChosen(modelId: String) {
        scope.launch {
            val setupId = preferencesRepository.observeSetupModelId().first() ?: return@launch
            if (setupId != modelId) cancel()
        }
    }

    private fun setUp(pick: (List<AIModel>, Int) -> AIModel?, onPicked: (String) -> Unit = {}) {
        scope.launch {
            // The model rows may not exist yet: the model manager has never been opened
            catalogSync.reconcile()
            val ramGb = DeviceMemory.totalRamGb(context)
            val model = pick(modelRepository.getAllModels().first(), ramGb)
            if (model == null) {
                Timber.i("No on-device model fits ${ramGb} GB RAM; leaving the choice to the model manager")
                return@launch
            }
            Timber.i("Setting up ${model.id} for $ramGb GB RAM")
            onPicked(model.id)
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
            if (id == fallbackModelId) preferencesRepository.setCpuFallbackDeclined(true)
            downloadManager.cancelDownload(id)
            forget()
        }
    }

    /**
     * The download marked the model as the most recent one, which makes it active; a model
     * still loaded in memory (e.g. the debug Demo) has to go so the next summary loads it.
     */
    private suspend fun activate(model: AIModel) {
        // After a running summary, which keeps the model it started with
        modelGate.withModel {
            if (aiEngine.isModelLoaded() && aiEngine.getModelInfo()?.path != model.localFilePath) {
                aiEngine.unloadModel()
            }
        }
        Timber.i("Model ${model.id} is set up")
    }

    private suspend fun forget() = preferencesRepository.setSetupModelId(null)

}
