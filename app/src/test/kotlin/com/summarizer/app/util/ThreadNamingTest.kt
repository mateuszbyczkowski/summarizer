package com.summarizer.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadNamingTest {

    @Test
    fun `notification decorations do not change the thread id`() {
        val id = ThreadNaming.threadIdFor("Family")
        assertEquals(id, ThreadNaming.threadIdFor("Family (5 messages)"))
        assertEquals(id, ThreadNaming.threadIdFor("  family  [2 messages] "))
        assertEquals(id, ThreadNaming.threadIdFor("FAMILY (3 new)"))
    }

    @Test
    fun `display name keeps case but drops decorations`() {
        assertEquals("Family Trip", ThreadNaming.displayName("Family  Trip (2 messages)"))
    }

    @Test
    fun `thread ids stay compatible with the historical scheme`() {
        assertEquals("family".hashCode().toString(), ThreadNaming.threadIdFor("Family"))
    }

    @Test
    fun `avatar initial skips emoji and symbols`() {
        assertEquals("T", ThreadNaming.initial("🧪 Test: Klasa 3B – rodzice"))
        assertEquals("3", ThreadNaming.initial("\"3B\" parents"))
        assertEquals("Ś", ThreadNaming.initial("świetlica"))
        assertEquals("#", ThreadNaming.initial("🎉🎉"))
        assertEquals("#", ThreadNaming.initial(""))
    }
}
