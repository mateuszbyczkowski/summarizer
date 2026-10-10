package com.summarizer.app.data.download

import com.summarizer.app.BuildConfig
import com.summarizer.app.data.ai.DemoEngine
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** The on-device models the app offers. */
object ModelCatalog {

    // On-device catalog (Oct 2026), chosen from measurements on a mid-range phone
    // (Dimensity 900, LocalModelBenchmark / LiteRtBenchmark on the debug sample chat):
    // - Gemma 4 on LiteRT-LM (GPU): ~160 tok/s prompt reading, ~30-60 s per summary,
    //   correct Polish. Default.
    // - LFM2.5 on llama.cpp (CPU): fallback for phones whose GPU cannot run Gemma; also
    //   used automatically when GPU loading fails.
    // Qwen3.5 / Gemma 3n GGUFs were dropped: 2-6 min per summary with weaker output.
    // URLs are pinned to a commit and checksum-verified after download.
    val DEFAULT_MODELS = listOf(
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

    /** What the model manager lists: debug builds add the Demo model (no download), see [DemoEngine]. */
    val CATALOG: List<AIModel> = listOfNotNull(DemoEngine.MODEL.takeIf { BuildConfig.DEBUG }) + DEFAULT_MODELS
}

/**
 * Brings the models stored in the database up to date with [ModelCatalog.CATALOG]. Runs when the
 * model manager opens and before onboarding starts the first download, whichever comes first.
 */
@Singleton
class ModelCatalogSync @Inject constructor(
    private val modelRepository: ModelRepository
) {
    private val mutex = Mutex()

    /**
     * - Refreshes catalog metadata (URL, checksum, size, text) of known models, keeping their
     *   download state.
     * - Deletes rows (and files) of models no longer in the catalog, so users upgrading from an
     *   older model list don't see stale entries.
     * - Inserts models new to the catalog.
     */
    suspend fun reconcile() = mutex.withLock {
        modelRepository.refreshDownloadState()
        val existing = modelRepository.getAllModels().first()
        val catalogIds = ModelCatalog.CATALOG.map { it.id }.toSet()

        val staleIds = existing.map { it.id }.filterNot { it in catalogIds }
        for (id in staleIds) {
            Timber.i("Removing stale catalog model: $id")
            modelRepository.deleteModelFile(id) // best-effort, ignored if not downloaded
            modelRepository.deleteModel(id)
        }

        val existingById = existing.associateBy { it.id }
        ModelCatalog.CATALOG.forEach { catalogModel ->
            val current = existingById[catalogModel.id] ?: return@forEach
            val refreshed = catalogModel.copy(
                isDownloaded = current.isDownloaded,
                localFilePath = current.localFilePath
            )
            if (refreshed != current) modelRepository.updateCatalogMetadata(refreshed)
        }

        val toInsert = ModelCatalog.CATALOG.filterNot { it.id in existingById.keys }
        if (toInsert.isNotEmpty()) {
            Timber.i("Seeding catalog with ${toInsert.size} new model(s): ${toInsert.map { it.id }}")
            modelRepository.insertModels(toInsert)
        }
    }
}
