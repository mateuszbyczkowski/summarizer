package com.summarizer.app.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesStoreTest {

    @Test
    fun `new query goes first`() {
        assertEquals(listOf("basen", "wycieczka"), RecentSearchesStore.withQuery(listOf("wycieczka"), "basen"))
    }

    @Test
    fun `query is trimmed and blank queries are ignored`() {
        assertEquals(listOf("zebranie"), RecentSearchesStore.withQuery(emptyList(), "  zebranie "))
        assertEquals(listOf("a"), RecentSearchesStore.withQuery(listOf("a"), "   "))
    }

    @Test
    fun `repeated query moves to the front without duplicates, ignoring case`() {
        val result = RecentSearchesStore.withQuery(listOf("basen", "Wycieczka", "zebranie"), "wycieczka")
        assertEquals(listOf("wycieczka", "basen", "zebranie"), result)
    }

    @Test
    fun `only the newest entries are kept`() {
        val full = (1..8).map { "q$it" }
        val result = RecentSearchesStore.withQuery(full, "new")
        assertEquals(RecentSearchesStore.MAX_RECENT_SEARCHES, result.size)
        assertEquals("new", result.first())
        assertEquals("q7", result.last())
    }

    @Test
    fun `stored value round-trips and bad data reads as empty`() {
        val queries = listOf("składka 50 zł", "\"quoted\"", "a,b")
        assertEquals(queries, RecentSearchesStore.decode(RecentSearchesStore.encode(queries)))
        assertEquals(emptyList<String>(), RecentSearchesStore.decode(null))
        assertEquals(emptyList<String>(), RecentSearchesStore.decode("not json"))
    }
}
