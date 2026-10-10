package com.summarizer.app.ui.screens.models

import androidx.annotation.StringRes
import com.summarizer.app.R
import com.summarizer.app.data.ai.DemoEngine
import com.summarizer.app.domain.model.AIModel

/** Where a local model runs. */
enum class ModelBackend { GPU, CPU, DEMO }

/**
 * How the model manager describes a catalog model in terms users care about: a plain name
 * ("Recommended"), the typical time per summary on a mid-range phone and where it runs.
 *
 * @property plainName Plain-language name shown first; null for models the app does not know
 *   (the technical name is shown instead)
 * @property typicalSeconds Typical time for one summary, from LocalModelBenchmark / LiteRtBenchmark
 *   runs on the debug sample chat (Dimensity 900, 2026-10-10: E2B 50 s, E4B 143 s, LFM2.5 165 s);
 *   null when unknown
 * @property languageNote How well the model handles Polish and other languages
 */
data class ModelProfile(
    @StringRes val plainName: Int?,
    val typicalSeconds: Int?,
    val backend: ModelBackend,
    @StringRes val languageNote: Int?
)

object ModelProfiles {

    const val GEMMA_E2B_ID = "gemma-4-e2b-gpu"
    const val GEMMA_E4B_ID = "gemma-4-e4b-gpu"
    const val LFM_ID = "lfm2.5-1.2b-instruct"

    /** Display order of the catalog in the model manager: best default first, demo last. */
    private val ORDER = listOf(GEMMA_E2B_ID, GEMMA_E4B_ID, LFM_ID, DemoEngine.MODEL_ID)

    fun profileOf(model: AIModel): ModelProfile = when (model.id) {
        GEMMA_E2B_ID -> ModelProfile(R.string.engine_plain_recommended, 50, ModelBackend.GPU, R.string.engine_language_gemma)
        GEMMA_E4B_ID -> ModelProfile(R.string.engine_plain_best_quality, 145, ModelBackend.GPU, R.string.engine_language_gemma_e4b)
        LFM_ID -> ModelProfile(R.string.engine_plain_backup_cpu, 165, ModelBackend.CPU, R.string.engine_language_lfm)
        DemoEngine.MODEL_ID -> ModelProfile(R.string.engine_plain_demo, 10, ModelBackend.DEMO, R.string.engine_language_demo)
        else -> ModelProfile(null, null, backendFromUrl(model.downloadUrl), null)
    }

    /** `.litertlm` models run on LiteRT-LM (GPU), `.gguf` on llama.cpp (CPU); see AIEngineProvider. */
    internal fun backendFromUrl(url: String): ModelBackend =
        if (url.endsWith(".litertlm", ignoreCase = true)) ModelBackend.GPU else ModelBackend.CPU

    /** Catalog names carry the backend in parentheses ("Gemma 4 E2B (GPU)"); this drops it. */
    fun technicalName(model: AIModel): String = technicalName(model.name)

    fun technicalName(catalogName: String): String = catalogName.substringBefore(" (").trim()

    /** The catalog sorted for display; unknown models keep their order after the known ones. */
    fun sorted(models: List<AIModel>): List<AIModel> =
        models.sortedBy { model -> ORDER.indexOf(model.id).let { if (it < 0) ORDER.size else it } }

    /** Whether the phone has too little memory to run [model]. Already downloaded models stay usable. */
    fun needsMoreRam(model: AIModel, deviceRamGb: Int): Boolean =
        !model.isDownloaded && model.minimumRAM > deviceRamGb
}
