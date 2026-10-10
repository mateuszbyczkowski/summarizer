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
            2) <topic in a few words>
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
    fun `labels without colons are recognized and repeated bullets removed`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW
            Wycieczka w piątek, zbiórka 7:15.
            TOPICS
            - Wycieczka
            ACTIONS
            - Przelew 85 zł do środy
            - Przelew 85 zł do środy
            ANNOUNCEMENTS
            - Zebranie we wtorek 18:00
            """.trimIndent()
        )

        assertEquals("Wycieczka w piątek, zbiórka 7:15.", parsed.overview)
        assertEquals(listOf("Wycieczka"), parsed.topics)
        assertEquals(listOf("Przelew 85 zł do środy"), parsed.actions)
        assertEquals(listOf("Zebranie we wtorek 18:00"), parsed.announcements)
    }

    @Test
    fun `inline comma lists and misspelled labels are handled`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Wycieczka do Centrum Nauki.
            TOPICS: plan wycieczki, koszt, zgody rodziców
            ANNOUNCEMTY: Zmiana autobusu na 7:30.
            """.trimIndent()
        )

        assertEquals(listOf("plan wycieczki", "koszt", "zgody rodziców"), parsed.topics)
        assertEquals(listOf("Zmiana autobusu na 7:30"), parsed.announcements)
    }

    @Test
    fun `label variants are recognized and content lines are not taken for labels`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Wycieczka w piątek.
            Key Topics:
            - Wycieczka
            Action items:
            - Dziadek: odbierze Zosię o 15:00
            - Zadanie: przelew 85 zł
            """.trimIndent()
        )

        assertEquals(listOf("Wycieczka"), parsed.topics)
        assertEquals(listOf("Dziadek: odbierze Zosię o 15:00", "Zadanie: przelew 85 zł"), parsed.actions)
    }

    @Test
    fun `reply without labels becomes the overview`() {
        val parsed = SummaryResponseParser.parse("Everyone agreed to meet at 7 at the park.")

        assertEquals("Everyone agreed to meet at 7 at the park.", parsed.overview)
        assertTrue(parsed.topics.isEmpty())
    }
}
