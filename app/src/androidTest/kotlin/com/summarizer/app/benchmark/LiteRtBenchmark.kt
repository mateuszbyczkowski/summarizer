package com.summarizer.app.benchmark

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.summarizer.app.data.ai.AndroidLanguageDetector
import com.summarizer.app.data.debug.DebugSampleData
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.ai.SummaryResponseParser
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Experiment: Google LiteRT-LM (GPU/CPU) on the debug sample chat, with the app's real prompt.
 *
 *   adb shell am instrument -w -e class com.summarizer.app.benchmark.LiteRtBenchmark#download \
 *       -e repo litert-community/gemma-4-E2B-it-litert-lm -e file gemma-4-E2B-it-gpu.litertlm \
 *       -e rev <commit> com.summarizer.app.debug.test/androidx.test.runner.AndroidJUnitRunner
 *   adb shell am instrument -w -e class com.summarizer.app.benchmark.LiteRtBenchmark#compareBackends ...
 * Logs with tag "LiteRtBenchmark". Downloads go to files/litertlm/; the app's own models
 * (files/models/) are compared too, e.g. `-e model gemma-4-e2b -e backends gpu,cpu`.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtBenchmark {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val modelDir = File(context.filesDir, "litertlm").apply { mkdirs() }

    @Test
    fun download() {
        val repo = args.getString("repo") ?: return
        val file = args.getString("file") ?: return
        val rev = args.getString("rev") ?: "main"
        val target = File(modelDir, file)
        if (target.exists()) {
            log("Already downloaded: $file (${target.length() / 1_000_000} MB)")
            return
        }

        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
        val expectedSha = client.newCall(Request.Builder().url("https://huggingface.co/api/models/$repo/revision/$rev?blobs=true").build())
            .execute().use { response ->
                com.google.gson.JsonParser.parseString(response.body.string()).asJsonObject["siblings"].asJsonArray
                    .map { it.asJsonObject }
                    .first { it["rfilename"].asString == file }["lfs"].asJsonObject["sha256"].asString
            }

        val partial = File(modelDir, "$file.part")
        val started = SystemClock.elapsedRealtime()
        client.newCall(Request.Builder().url("https://huggingface.co/$repo/resolve/$rev/$file").build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val total = response.body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastLog = 0L
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done - lastLog > 200_000_000) {
                            log("Downloaded ${done / 1_000_000} / ${total / 1_000_000} MB")
                            lastLog = done
                        }
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualSha == expectedSha) { "Checksum mismatch: $actualSha != $expectedSha" }
        }
        check(partial.renameTo(target))
        log("Downloaded $file in ${(SystemClock.elapsedRealtime() - started) / 1000}s, checksum verified")
    }

    @Test
    fun compareBackends() {
        val filter = args.getString("model").orEmpty()
        // Benchmark downloads and the app's own models (files/models)
        val models = listOf(modelDir, File(context.filesDir, "models"))
            .flatMap { dir -> dir.listFiles { f -> f.extension == "litertlm" && filter in f.name }.orEmpty().toList() }
        assumeTrue("No LiteRT-LM model", models.isNotEmpty())
        val backends = (args.getString("backends") ?: "gpu,cpu").split(",")

        val messages = DebugSampleData(NoOpMessageRepository).sampleMessages(System.currentTimeMillis())
        val language = runBlocking {
            AndroidLanguageDetector(context).detectLanguageName(messages.joinToString("\n") { it.content })
        }
        val prompt = PromptTemplate.buildSummarizationPrompt(messages, DebugSampleData.THREAD_NAME, 8_500, language)!!
        log("Language=$language prompt=${prompt.length} chars")

        for (model in models) for (backendName in backends) {
            runCatching { run(model, backendName, prompt, language) }
                .onFailure { log("${model.name} $backendName FAILED: ${it::class.simpleName}: ${it.message}") }
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun run(model: File, backendName: String, prompt: String, language: String?) {
        val backend = when (backendName) {
            "gpu" -> Backend.GPU()
            else -> Backend.CPU((Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6))
        }
        ExperimentalFlags.enableBenchmark = true
        val initStart = SystemClock.elapsedRealtime()
        Engine(EngineConfig(modelPath = model.path, backend = backend, maxNumTokens = 4096, cacheDir = context.cacheDir.path)).use { engine ->
            engine.initialize()
            val initMs = SystemClock.elapsedRealtime() - initStart

            val config = ConversationConfig(
                systemInstruction = Contents.of(PromptTemplate.systemPrompt(language)),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.2, seed = 0)
            )
            engine.createConversation(config).use { conversation ->
                val start = SystemClock.elapsedRealtime()
                val reply = conversation.sendMessage(prompt)
                val totalMs = SystemClock.elapsedRealtime() - start
                val text = reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                log("===== ${model.name} [$backendName] init=${initMs}ms total=${totalMs}ms")
                runCatching { conversation.getBenchmarkInfo() }
                    .onSuccess { info ->
                        log(
                            "prefill=${info.lastPrefillTokenCount} tok @ ${"%.1f".format(info.lastPrefillTokensPerSecond)} tok/s, " +
                                "decode=${info.lastDecodeTokenCount} tok @ ${"%.1f".format(info.lastDecodeTokensPerSecond)} tok/s, " +
                                "ttft=${"%.1f".format(info.timeToFirstTokenInSecond)}s"
                        )
                    }
                    .onFailure { log("benchmark info unavailable: ${it.message}") }
                text.lines().forEach { log("| $it") }
                val parsed = SummaryResponseParser.parse(text)
                log("parsed overview=${parsed.overview.length} chars, topics=${parsed.topics.size}, actions=${parsed.actions.size}, announcements=${parsed.announcements.size}")
            }
        }
    }

    private fun log(message: String) {
        Log.i("LiteRtBenchmark", message)
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
