package com.summarizer.app.data.ai

import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.ai.AIEngineError
import com.summarizer.app.domain.ai.ModelInfo
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.util.suspendRunCatching
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds only: a pretend on-device model that "reads" for a moment and then streams a
 * fixed summary of the debug sample chat over about ten seconds. Lets generation progress,
 * streaming, cancel and notifications be exercised where real models cannot be downloaded
 * (emulators on the corporate network). Selected through the [MODEL] catalog entry.
 */
@Singleton
class DemoEngine @Inject constructor() : AIEngine {

    companion object {
        const val MODEL_ID = "debug-demo"

        /** Stands in for a model file; nothing exists at this path. */
        const val MODEL_PATH = "debug-demo"

        val MODEL = AIModel(
            id = MODEL_ID,
            name = "Demo (debug)",
            description = "Debug builds only. Streams a fixed summary of the test chat in about 10 s; no download.",
            sizeInMB = 0,
            downloadUrl = "",
            isDownloaded = true,
            minimumRAM = 1,
            estimatedSpeed = "Fast",
            localFilePath = MODEL_PATH
        )

        private const val READING_MS = 2_000L
        private const val WRITING_MS = 8_000L
        private const val MAX_PROMPT_CHARS = 14_000

        // Matches DebugSampleData, including the change of departure time and an unresolvable deadline
        private val SUMMARY = """
            OVERVIEW: W piątek 17.10 klasa jedzie do Centrum Nauki Kopernik; autobus odjeżdża wcześniej, o 7:30, zbiórka o 7:15. Zebranie rodziców we wtorek 21.10 o 18:00.
            TOPICS:
            - Wycieczka do Centrum Nauki Kopernik
            - Zebranie rodziców i składka na radę rodziców
            - Basen odwołany w tym tygodniu
            ACTIONS:
            - Przelew 85 zł za wycieczkę na konto klasowe (do 15.10)
            - Podpisać i oddać zgody na wycieczkę (do poniedziałku)
            - Marek zabiera Zosię na zbiórkę o 7:00 (pt 17.10)
            - Składka 50 zł na radę rodziców (do 31.10)
            ANNOUNCEMENTS:
            - Zmiana: autobus o 7:30, zbiórka o 7:15 przy głównej bramie
            - W czwartek zamiast basenu zajęcia w sali gimnastycznej, strój sportowy
            DATES:
            - 16.10 | Sala gimnastyczna zamiast basenu, strój sportowy
            - 17.10 7:15 | Zbiórka na wycieczkę, główna brama szkoły
            - 21.10 18:00 | Zebranie rodziców, sala 12
        """.trimIndent()
    }

    @Volatile
    private var loaded = false

    @Volatile
    private var cancelled = false

    override suspend fun loadModel(modelPath: String): Result<Unit> = suspendRunCatching {
        if (modelPath != MODEL_PATH) throw AIEngineError.ModelNotFound(modelPath)
        loaded = true
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float,
        onPartial: ((String) -> Unit)?
    ): Result<String> = suspendRunCatching {
        if (!loaded) throw AIEngineError.ModelNotLoaded
        cancelled = false
        Timber.d("Demo engine: streaming the canned summary")
        // Coroutine cancellation interrupts delay(); cancelGeneration() stops at the next word
        delay(READING_MS)
        val words = SUMMARY.split(Regex("(?<=\\s)"))
        val output = StringBuilder()
        for (word in words) {
            if (cancelled) break
            output.append(word)
            onPartial?.invoke(output.toString())
            delay(WRITING_MS / words.size)
        }
        output.toString().trim()
    }

    override suspend fun maxPromptChars(): Int = MAX_PROMPT_CHARS

    override fun cancelGeneration() {
        cancelled = true
    }

    override suspend fun unloadModel() {
        loaded = false
    }

    override suspend fun isModelLoaded(): Boolean = loaded

    override suspend fun getModelInfo(): ModelInfo? =
        if (loaded) ModelInfo(name = "Demo", path = MODEL_PATH, contextLength = MAX_PROMPT_CHARS) else null

    override suspend fun engineName(): String? = if (loaded) "Demo" else null
}
