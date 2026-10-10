package com.summarizer.app.benchmark

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.summarizer.app.data.ai.AndroidLanguageDetector
import com.summarizer.app.data.debug.DebugSampleData
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.ai.SummaryResponseParser
import com.summarizer.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import com.summarizer.app.domain.model.Message
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Measures on-device generation speed for different llama.cpp settings, using a downloaded
 * model and a synthetic chat (no user data). Skipped when no model is downloaded.
 *
 * Run on a phone without uninstalling the app (connectedAndroidTest would uninstall it):
 *   ./gradlew assembleDebugAndroidTest
 *   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *   adb shell am instrument -w -e class com.summarizer.app.benchmark.LocalModelBenchmark \
 *       com.summarizer.app.debug.test/androidx.test.runner.AndroidJUnitRunner
 * Results are logged with tag "LlamaBenchmark".
 */
@RunWith(AndroidJUnit4::class)
class LocalModelBenchmark {

    private data class Config(val threads: Int, val batchSize: Int = 512, val flashAttention: Boolean = false)

    private val bridge = LlamaBridge

    @Test
    fun compareSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = File(context.filesDir, "models").listFiles { f -> f.extension == "gguf" }.orEmpty()
        assumeTrue("No downloaded model", models.isNotEmpty())

        val cores = Runtime.getRuntime().availableProcessors()
        val configs = listOf(2, 4, 6, cores).distinct().filter { it <= cores }.map { Config(it) } +
            Config(threads = 4, flashAttention = true) +
            Config(threads = 4, batchSize = 128)

        val prompt = PromptTemplate.buildSummarizationPrompt(syntheticChat(), "Class 3B parents", 8_500)!!
        log("Device cores=$cores, prompt=${prompt.length} chars")

        for (model in models) {
            for (config in configs) {
                runCatching { measure(model, config, prompt) }
                    .onFailure { log("${model.name} $config FAILED: ${it.message}") }
            }
        }
        bridge.shutdown()
    }

    /**
     * Summarizes the debug sample chat with every downloaded model using the app's real prompt,
     * logging speed, the raw output and what the parser extracts. Filter with `-e model <name>`.
     */
    @Test
    fun compareModels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filter = InstrumentationRegistry.getArguments().getString("model").orEmpty()
        val models = File(context.filesDir, "models").listFiles { f -> f.extension == "gguf" && filter in f.name }.orEmpty()
        assumeTrue("No downloaded model", models.isNotEmpty())

        val messages = DebugSampleData(NoOpMessageRepository).sampleMessages(System.currentTimeMillis())
        val language = runBlocking {
            AndroidLanguageDetector(context).detectLanguageName(messages.joinToString("\n") { it.content })
        }
        val prompt = PromptTemplate.buildSummarizationPrompt(messages, DebugSampleData.THREAD_NAME, 8_500, language)!!
        log("Detected language: $language, prompt=${prompt.length} chars")
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

        for (model in models.sortedBy { it.length() }) {
            val (output, stats) = runCatching { generate(model, Config(threads), prompt, maxTokens = 400) }
                .getOrElse { log("${model.name} FAILED: ${it.message}"); continue }
            log("===== ${model.name}: $stats")
            output.lines().forEach { log("| $it") }
            val parsed = SummaryResponseParser.parse(output)
            log("parsed overview=${parsed.overview.length} chars, topics=${parsed.topics.size}, actions=${parsed.actions.size}, announcements=${parsed.announcements.size}")
        }
        bridge.shutdown()
    }

    private fun measure(model: File, config: Config, prompt: String) {
        val (output, stats) = generate(model, config, prompt, maxTokens = 96)
        log("${model.name} $config $stats (${output.length} chars)")
    }

    private fun generate(model: File, config: Config, prompt: String, maxTokens: Int): Pair<String, String> {
        bridge.shutdown()
        applyParams(config, maxTokens)
        val loadStart = SystemClock.elapsedRealtime()
        check(bridge.initGenerateModel(model.path)) { "init failed" }
        applyParams(config, maxTokens)
        val loadMs = SystemClock.elapsedRealtime() - loadStart

        val template = bridge.getModelChatTemplate().orEmpty()
        var chatPrompt = bridge.applyChatTemplate(
            listOf("system" to PromptTemplate.SYSTEM_PROMPT, "user" to prompt),
            addAssistantPrefix = true
        ) ?: error("no chat template")
        if ("<think>" in template) chatPrompt += "<think>\n\n</think>\n\n"

        val done = CountDownLatch(1)
        val output = StringBuilder()
        var firstTokenAt = 0L
        val start = SystemClock.elapsedRealtime()
        bridge.generateStream(chatPrompt, object : GenStream {
            override fun onDelta(text: String) {
                if (firstTokenAt == 0L) firstTokenAt = SystemClock.elapsedRealtime()
                output.append(text)
            }
            override fun onComplete() = done.countDown()
            override fun onError(message: String) = done.countDown()
        })
        done.await(10, TimeUnit.MINUTES)
        val end = SystemClock.elapsedRealtime()

        val promptMs = firstTokenAt - start
        val genMs = end - firstTokenAt
        val promptTokens = prompt.length / 3.0 // rough; same prompt for every config
        val stats = "load=${loadMs}ms prompt=${promptMs}ms (~${"%.0f".format(promptTokens * 1000 / promptMs)} tok/s) " +
            "gen=${genMs}ms (~${"%.1f".format(output.length / 4.0 * 1000 / genMs)} tok/s)"
        return output.toString() to stats
    }

    private fun applyParams(config: Config, maxTokens: Int) {
        bridge.updateGenerateParams(
            temperature = 0.2f,
            maxTokens = maxTokens,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextLength = 8_192,
            numThreads = config.threads,
            useMmap = true,
            flashAttention = config.flashAttention,
            batchSize = config.batchSize
        )
    }

    private fun syntheticChat(): List<Message> {
        val lines = listOf(
            "Anna" to "Hi all, the school trip is moved to Friday because of the weather",
            "Piotr" to "Thanks! What time does the bus leave?",
            "Anna" to "8:00 from the main gate, please be there by 7:45",
            "Kasia" to "Do the kids need packed lunch?",
            "Anna" to "Yes, lunch and a water bottle. Cost is 45 zł per child",
            "Tomek" to "Can we pay by bank transfer?",
            "Anna" to "Yes, transfer to the class account by Wednesday please",
            "Piotr" to "Who is collecting the consent forms?",
            "Kasia" to "I can collect them on Monday morning",
            "Anna" to "Great, thank you Kasia. Also parent meeting next Tuesday at 18:00",
            "Tomek" to "Will it be online or at school?",
            "Anna" to "At school, room 12",
            "Piotr" to "Ok, see you there"
        )
        return lines.mapIndexed { i, (sender, text) ->
            Message(threadId = "bench", threadName = "Class 3B parents", sender = sender, content = text, timestamp = 1_700_000_000_000L + i * 60_000L)
        }
    }

    private fun log(message: String) {
        Log.i("LlamaBenchmark", message)
    }

    private object NoOpMessageRepository : MessageRepository {
        override fun getMessagesForThread(threadId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun getRecentMessagesForThread(threadId: String, limit: Int) = emptyList<Message>()
        override suspend fun getMessagesForThreadSince(threadId: String, sinceTimestamp: Long) = emptyList<Message>()
        override suspend fun saveIncomingMessage(message: Message) = false
        override suspend fun deleteMessagesForThread(threadId: String) = Unit
        override suspend fun searchMessages(query: String) = emptyList<Message>()
    }
}
