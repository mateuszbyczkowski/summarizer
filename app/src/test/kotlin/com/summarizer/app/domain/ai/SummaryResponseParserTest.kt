package com.summarizer.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryResponseParserTest {

    @Test
    fun `well formed reply is split into sections`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: The class trip moves to Friday. Parents agreed to share costs.
            TOPICS:
            - Trip date
            - Costs
            ACTIONS:
            - Anna books the bus by Wednesday
            ANNOUNCEMENTS:
            - Trip moved to Friday 8:00
            """.trimIndent()
        )

        assertEquals("The class trip moves to Friday. Parents agreed to share costs.", parsed.overview)
        assertEquals(listOf("Trip date", "Costs"), parsed.topics)
        assertEquals(listOf("Anna books the bus by Wednesday"), parsed.actions)
        assertEquals(listOf("Trip moved to Friday 8:00"), parsed.announcements)
    }

    @Test
    fun `markdown, numbering, None and copied hints are cleaned up`() {
        val parsed = SummaryResponseParser.parse(
            """
            **OVERVIEW:**
            Wycieczka przeniesiona na piątek.
            **TOPICS:**
            1. Termin wycieczki
            2) short topic
            **ACTIONS:**
            - Brak
            ## ANNOUNCEMENTS:
            * None
            """.trimIndent()
        )

        assertEquals("Wycieczka przeniesiona na piątek.", parsed.overview)
        assertEquals(listOf("Termin wycieczki"), parsed.topics)
        assertTrue(parsed.actions.isEmpty())
        assertTrue(parsed.announcements.isEmpty())
    }

    @Test
    fun `reply without labels becomes the overview`() {
        val parsed = SummaryResponseParser.parse("Everyone agreed to meet at 7 at the park.")

        assertEquals("Everyone agreed to meet at 7 at the park.", parsed.overview)
        assertTrue(parsed.topics.isEmpty())
    }
}
