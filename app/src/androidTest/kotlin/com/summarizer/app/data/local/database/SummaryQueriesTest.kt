package com.summarizer.app.data.local.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.data.local.entity.SummaryEntity
import com.summarizer.app.data.local.entity.ThreadEntity
import com.summarizer.app.data.repository.ThreadRepositoryImpl
import com.summarizer.app.domain.model.ActionItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Thread overview, latest summaries and task/rating updates against a real (unencrypted) Room database. */
@RunWith(AndroidJUnit4::class)
class SummaryQueriesTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun message(threadId: String, timestamp: Long) = MessageEntity(
        threadId = threadId,
        threadName = threadId,
        sender = "Anna",
        content = "m$timestamp",
        timestamp = timestamp,
        messageHash = "$threadId-$timestamp"
    )

    private fun summary(threadId: String, endTimestamp: Long, generatedAt: Long, actions: List<ActionItem> = emptyList()) = SummaryEntity(
        threadId = threadId,
        threadName = threadId,
        keyTopics = emptyList(),
        actionItems = actions,
        announcements = emptyList(),
        participantHighlights = emptyList(),
        messageCount = 1,
        startTimestamp = 0,
        endTimestamp = endTimestamp,
        generatedAt = generatedAt
    )

    @Test
    fun overviewCountsMessagesAfterTheLatestSummaryAndOpenTasks() = runBlocking {
        db.threadDao().insertIfAbsent(ThreadEntity("a", "a", lastMessageTimestamp = 400))
        db.threadDao().insertIfAbsent(ThreadEntity("b", "b", lastMessageTimestamp = 300))
        listOf(100L, 200L, 300L, 400L).forEach { db.messageDao().insert(message("a", it)) }
        listOf(100L, 300L).forEach { db.messageDao().insert(message("b", it)) }
        db.summaryDao().insert(summary("a", endTimestamp = 100, generatedAt = 1_000))
        db.summaryDao().insert(
            summary("a", endTimestamp = 200, generatedAt = 2_000, actions = listOf(ActionItem("x"), ActionItem("y", done = true)))
        )

        val overviews = ThreadRepositoryImpl(db.threadDao(), db.summaryDao()).getThreadOverviews().first()

        assertEquals(listOf("a", "b"), overviews.map { it.thread.threadId })
        assertEquals(200L, overviews[0].latestSummary?.endTimestamp)
        assertEquals(2, overviews[0].newSinceSummary)
        assertEquals(1, overviews[0].openTasks)
        assertNull(overviews[1].latestSummary)
        assertEquals(2, overviews[1].newSinceSummary)
        assertEquals(0, overviews[1].openTasks)
        assertEquals("Anna", overviews[0].lastMessageSender)
        assertEquals("m400", overviews[0].lastMessageText)
        assertEquals("m300", overviews[1].lastMessageText)
    }

    @Test
    fun taskDoneAndRatingAreSaved() = runBlocking {
        val id = db.summaryDao().insert(summary("a", 100, 1_000, actions = listOf(ActionItem("x", due = "2026-10-15"), ActionItem("y"))))

        db.summaryDao().setActionItemDone(id, 1, true)
        db.summaryDao().setActionItemDone(id, 5, true) // out of range: ignored
        db.summaryDao().setRating(id, -1)

        val saved = db.summaryDao().getSummary(id)!!
        assertEquals(listOf(ActionItem("x", due = "2026-10-15"), ActionItem("y", done = true)), saved.actionItems)
        assertEquals(-1, saved.rating)
    }
}
