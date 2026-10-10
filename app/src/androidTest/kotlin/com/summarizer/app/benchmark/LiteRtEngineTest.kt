package com.summarizer.app.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summarizer.app.data.ai.AndroidLanguageDetector
import com.summarizer.app.data.ai.LiteRtEngine
import com.summarizer.app.data.debug.DebugSampleData
import com.summarizer.app.domain.ai.PromptTemplate
import com.summarizer.app.domain.ai.SummaryResponseParser
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the app's LiteRtEngine on a downloaded `.litertlm` model with the real prompt.
 * Skipped when no LiteRT-LM model is downloaded (CI, emulators). Logs with tag "LiteRtEngineTest".
 */
@RunWith(AndroidJUnit4::class)
class LiteRtEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun summarizesSampleChat() = runBlocking {
        val model = File(context.filesDir, "models").listFiles { f -> f.extension == "litertlm" }?.firstOrNull()
        assumeTrue("No LiteRT-LM model downloaded", model != null)

        val engine = LiteRtEngine(context)
        engine.loadModel(model!!.path).getOrThrow()
        try {
            val messages = DebugSampleData(NoOpMessageRepository).sampleMessages(System.currentTimeMillis())
            val language = AndroidLanguageDetector(context).detectLanguageName(messages.joinToString("\n") { it.content })
            val prompt = PromptTemplate.buildSummarizationPrompt(messages, DebugSampleData.THREAD_NAME, engine.maxPromptChars(), language)!!

            val started = System.currentTimeMillis()
            val output = engine.generate(prompt, PromptTemplate.systemPrompt(language), maxTokens = 400, temperature = 0.2f).getOrThrow()
            Log.i("LiteRtEngineTest", "Generated in ${System.currentTimeMillis() - started}ms")
            output.lines().forEach { Log.i("LiteRtEngineTest", "| $it") }

            val parsed = SummaryResponseParser.parse(output)
            assertTrue("overview missing", parsed.overview.isNotBlank())
            assertTrue("no sections parsed", parsed.topics.isNotEmpty() || parsed.actions.isNotEmpty() || parsed.announcements.isNotEmpty())
        } finally {
            engine.unloadModel()
        }
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
