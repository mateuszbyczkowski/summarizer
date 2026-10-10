package com.summarizer.app.ui.screens.search

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultsTest {

    private fun summary(
        id: Long,
        threadId: String,
        overview: String = "",
        topics: List<String> = emptyList(),
        actions: List<ActionItem> = emptyList(),
        announcements: List<String> = emptyList(),
        keyDates: List<KeyDate> = emptyList()
    ) = Summary(
        id = id,
        threadId = threadId,
        threadName = "Chat $threadId",
        overview = overview,
        keyTopics = topics,
        actionItems = actions,
        announcements = announcements,
        participantHighlights = emptyList(),
        messageCount = 1,
        startTimestamp = 0,
        endTimestamp = 0,
        keyDates = keyDates
    )

    private fun message(id: Long, threadId: String, content: String) = Message(
        id = id,
        threadId = threadId,
        threadName = "Chat $threadId",
        sender = "Anna",
        content = content,
        timestamp = id
    )

    // --- matchRanges ---

    @Test
    fun `matches are case-insensitive, non-overlapping and include Polish letters`() {
        assertEquals(listOf(0..4, 8..12, 14..18), SearchText.matchRanges("Basen i basen BASEN", "basen"))
        assertEquals(listOf(0..1, 2..3), SearchText.matchRanges("aaaa", "aa"))
        assertEquals(listOf(9..13), SearchText.matchRanges("Składka: ŚRODA", "środa"))
    }

    @Test
    fun `blank query matches nothing`() {
        assertTrue(SearchText.matchRanges("text", " ").isEmpty())
    }

    // --- snippet ---

    @Test
    fun `short text is returned on one line`() {
        assertEquals("Zebranie we wtorek o 18:00", SearchText.snippet("Zebranie\nwe  wtorek o 18:00", "wtorek"))
    }

    @Test
    fun `long text is cut around the first match at a word boundary`() {
        val text = (1..40).joinToString(" ") { "słowo$it" } + " wycieczka " + (41..80).joinToString(" ") { "słowo$it" }
        val snippet = SearchText.snippet(text, "wycieczka", maxLength = 60)
        assertTrue(snippet.startsWith("…słowo"))
        assertTrue(snippet.endsWith("…"))
        assertTrue("wycieczka" in snippet)
        assertTrue(snippet.length <= 62)
    }

    @Test
    fun `long text without a match is cut from the start`() {
        val text = "a".repeat(200)
        val snippet = SearchText.snippet(text, "zzz", maxLength = 50)
        assertEquals("a".repeat(50) + "…", snippet)
    }

    @Test
    fun `summary snippet prefers the field that mentions the query`() {
        val s = summary(
            1, "t",
            overview = "Wycieczka w piątek.",
            topics = listOf("Zebranie rodziców"),
            announcements = listOf("Basen odwołany"),
            keyDates = listOf(KeyDate("Zbiórka", detail = "główna brama"))
        )
        assertEquals("Basen odwołany", SearchText.summarySnippet(s, "basen"))
        assertEquals("Zbiórka · główna brama", SearchText.summarySnippet(s, "brama"))
        assertEquals("Wycieczka w piątek.", SearchText.summarySnippet(s, "Chat"))
        assertEquals("A · B", SearchText.summarySnippet(summary(2, "t", topics = listOf("A", "B")), "x"))
    }

    // --- tasks, chats and filters ---

    @Test
    fun `tasks that mention the query, or are assigned to it, become task hits`() {
        val s = summary(
            1, "t",
            actions = listOf(
                ActionItem("Przelew 85 zł"),
                ActionItem("Zgody na wycieczkę", assignedTo = "Kasia"),
                ActionItem("Basen")
            )
        )
        assertEquals(listOf(0), SearchResultsLogic.taskHits(listOf(s), "przelew").map { it.index })
        assertEquals(listOf(1), SearchResultsLogic.taskHits(listOf(s), "kasia").map { it.index })
    }

    @Test
    fun `chats are distinct in order of appearance`() {
        val chats = SearchResultsLogic.chats(
            listOf(summary(1, "a"), summary(2, "b")),
            listOf(message(1, "b", "x"), message(2, "c", "y"))
        )
        assertEquals(listOf("a", "b", "c"), chats.map { it.threadId })
    }

    @Test
    fun `type and chat filters narrow the results`() {
        val summaries = listOf(summary(1, "a", actions = listOf(ActionItem("basen"))), summary(2, "b"))
        val tasks = SearchResultsLogic.taskHits(summaries, "basen")
        val messages = listOf(message(1, "a", "basen"), message(2, "b", "basen"))

        val all = SearchResultsLogic.apply(summaries, tasks, messages, SearchFilter())
        assertEquals(Triple(2, 1, 2), Triple(all.first.size, all.second.size, all.third.size))

        val onlyMessages = SearchResultsLogic.apply(summaries, tasks, messages, SearchFilter(type = SearchResultType.MESSAGES))
        assertEquals(Triple(0, 0, 2), Triple(onlyMessages.first.size, onlyMessages.second.size, onlyMessages.third.size))

        val chatB = SearchResultsLogic.apply(summaries, tasks, messages, SearchFilter(threadId = "b"))
        assertEquals(listOf("b"), chatB.first.map { it.threadId })
        assertTrue(chatB.second.isEmpty())
        assertEquals(listOf("b"), chatB.third.map { it.threadId })
    }

    @Test
    fun `a chat filter for a chat without results is ignored`() {
        val summaries = listOf(summary(1, "a"))
        val result = SearchResultsLogic.apply(summaries, emptyList(), emptyList(), SearchFilter(threadId = "gone"))
        assertEquals(1, result.first.size)
    }

    @Test
    fun `filtered success keeps the chat list of all results`() {
        val state = SearchUiState.Success(
            query = "x",
            summaries = listOf(summary(1, "a")),
            messages = listOf(message(1, "b", "x")),
            chats = SearchResultsLogic.chats(listOf(summary(1, "a")), listOf(message(1, "b", "x")))
        )
        val filtered = state.filtered(SearchFilter(threadId = "a"))
        assertEquals(2, filtered.chats.size)
        assertTrue(filtered.messages.isEmpty())
        assertEquals(SearchFilter(threadId = "a"), filtered.filter)
    }
}
