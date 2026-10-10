package com.summarizer.app.ui.screens.thread

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.ui.screens.thread.ThreadSummaryRules.EngineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ThreadSummaryRulesTest {

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun `engine kind from the recorded engine`() {
        assertEquals(EngineKind.OPENAI, ThreadSummaryRules.engineKind("OpenAI gpt-4o-mini"))
        assertEquals(EngineKind.ON_DEVICE, ThreadSummaryRules.engineKind("Gemma 4 E2B · GPU"))
        assertEquals(EngineKind.ON_DEVICE, ThreadSummaryRules.engineKind("Demo"))
        assertNull(ThreadSummaryRules.engineKind(null))
        assertNull(ThreadSummaryRules.engineKind(" "))
    }

    @Test
    fun `new messages are those after the latest summary`() {
        val messages = (1..5L).map { Message(id = it, threadId = "t", threadName = "C", sender = "A", content = "x", timestamp = it * 1000) }
        val summary = Summary(
            threadId = "t", threadName = "C", keyTopics = emptyList(), actionItems = emptyList(),
            announcements = emptyList(), participantHighlights = emptyList(), messageCount = 3,
            startTimestamp = 1000, endTimestamp = 3000
        )
        assertEquals(2, ThreadSummaryRules.newMessageCount(messages, summary))
        assertEquals(0, ThreadSummaryRules.newMessageCount(messages, null))
    }

    @Test
    fun `key dates are urgent from today until two days ahead`() {
        fun urgent(date: String?) = ThreadSummaryRules.isUrgent(KeyDate("x", date = date), today)
        assertTrue(urgent("2026-10-13"))
        assertTrue(urgent("2026-10-15"))
        assertFalse(urgent("2026-10-16"))
        assertFalse(urgent("2026-10-12"))
        assertFalse(urgent(null))
        assertFalse(urgent("w środę"))
    }

    @Test
    fun `open tasks are urgent when due soon or overdue`() {
        fun urgent(due: String?, done: Boolean = false) = ThreadSummaryRules.isUrgent(ActionItem("x", due = due, done = done), today)
        assertTrue(urgent("2026-10-15"))
        assertTrue(urgent("2026-10-10"))
        assertFalse(urgent("2026-10-16"))
        assertFalse(urgent("2026-10-14", done = true))
        assertFalse(urgent(null))
    }

    @Test
    fun `day ranges are compact`() {
        fun range(start: String, end: String) = ThreadSummaryRules.formatDayRange(LocalDate.parse(start), LocalDate.parse(end))
        assertEquals("13.10", range("2026-10-13", "2026-10-13"))
        assertEquals("10–13.10", range("2026-10-10", "2026-10-13"))
        assertEquals("28.09–3.10", range("2026-09-28", "2026-10-03"))
        assertEquals("28.12.2025–2.01.2026", range("2025-12-28", "2026-01-02"))
    }

    @Test
    fun `day range of timestamps`() {
        val start = LocalDate.of(2026, 10, 10).atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = LocalDate.of(2026, 10, 13).atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(
            LocalDate.of(2026, 10, 10) to LocalDate.of(2026, 10, 13),
            ThreadSummaryRules.dayRange(listOf(end, start), ZoneOffset.UTC)
        )
        assertNull(ThreadSummaryRules.dayRange(emptyList()))
    }
}
