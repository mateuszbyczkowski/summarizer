package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
        assertEquals(listOf("Anna books the bus by Wednesday"), parsed.actions.map { it.task })
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
        assertEquals(listOf("Przelew 85 zł do środy"), parsed.actions.map { it.task })
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
        assertEquals(listOf("Dziadek: odbierze Zosię o 15:00", "Zadanie: przelew 85 zł"), parsed.actions.map { it.task })
    }

    @Test
    fun `reply without labels becomes the overview`() {
        val parsed = SummaryResponseParser.parse("Everyone agreed to meet at 7 at the park.")

        assertEquals("Everyone agreed to meet at 7 at the park.", parsed.overview)
        assertTrue(parsed.topics.isEmpty())
    }

    @Test
    fun `dates section and task deadlines are structured`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Wycieczka w piątek 17.10.
            ACTIONS:
            - Przelew 85 zł na konto klasowe (do 15.10)
            - Oddać zgody (do poniedziałku)
            ANNOUNCEMENTS:
            - None
            DATES:
            - 17.10 7:30 | Wycieczka, autobus spod szkoły
            - 21.10 18:00 | Zebranie rodziców, sala 12
            - w środę | Basen
            """.trimIndent(),
            reference = LocalDate.of(2026, 10, 13)
        )

        assertEquals(
            listOf(
                ActionItem(task = "Przelew 85 zł na konto klasowe", due = "2026-10-15"),
                ActionItem(task = "Oddać zgody (do poniedziałku)")
            ),
            parsed.actions
        )
        assertEquals(
            listOf(
                KeyDate(label = "Wycieczka", date = "2026-10-17", time = "07:30", detail = "autobus spod szkoły"),
                KeyDate(label = "Zebranie rodziców", date = "2026-10-21", time = "18:00", detail = "sala 12"),
                KeyDate(label = "Basen", date = null, time = null, detail = "w środę")
            ),
            parsed.keyDates
        )
        assertTrue(parsed.announcements.isEmpty())
    }

    @Test
    fun `copied date placeholders are removed and Polish date labels recognized`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Spotkanie.
            ACTIONS:
            - <task someone must do and who> (by DD.MM)
            - Kupić bilety (by DD.MM)
            WAŻNE DATY:
            - DD.MM HH:MM | <event, place>
            - 2026-11-03 | Koncert
            """.trimIndent(),
            reference = LocalDate.of(2026, 10, 13)
        )

        assertEquals(listOf(ActionItem(task = "Kupić bilety")), parsed.actions)
        assertEquals(listOf(KeyDate(label = "Koncert", date = "2026-11-03")), parsed.keyDates)
    }

    @Test
    fun `streaming overview hides an unfinished label and follows the overview`() {
        assertEquals("", SummaryResponseParser.streamingOverview(""))
        assertEquals("", SummaryResponseParser.streamingOverview("**OVER"))
        assertEquals("", SummaryResponseParser.streamingOverview("OVERVIEW:"))
        assertEquals("Wycieczka w", SummaryResponseParser.streamingOverview("OVERVIEW: Wycieczka w"))
        assertEquals("Wycieczka.", SummaryResponseParser.streamingOverview("OVERVIEW: Wycieczka.\nTOPICS:\n- Wyc"))
        assertEquals("Everyone agreed", SummaryResponseParser.streamingOverview("Everyone agreed"))
    }

    @Test
    fun `date lines without a bullet keep their day`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Wycieczka w piątek.
            DATES:
            17.10 7:15 | Zbiórka
            1. 21.10 18:00 | Zebranie
            """.trimIndent(),
            reference = java.time.LocalDate.of(2026, 10, 13)
        )

        assertEquals(listOf("2026-10-17", "2026-10-21"), parsed.keyDates.map { it.date })
        assertEquals(listOf("07:15", "18:00"), parsed.keyDates.map { it.time })
    }

    @Test
    fun `date lines that only say who wrote when are dropped`() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Mecz w sobotę.
            DATES:
            - 13.10 14:05 | Anna odpowiedziała w wątku
            - 12.10 | Tomek napisał, że będzie
            - 13.10 | Kasia replied in the thread
            - w środę | Rodzice potwierdzili
            - 18.10 19:30-21:00 | Mecz, hala OSiR
            - 20.10 | Kasia potwierdziła wyjazd do Krakowa
            - 13.10 18:00 | Zebranie rodziców
            """.trimIndent(),
            reference = LocalDate.of(2026, 10, 13)
        )

        assertEquals(
            listOf("Mecz", "Kasia potwierdziła wyjazd do Krakowa", "Zebranie rodziców"),
            parsed.keyDates.map { it.label }
        )
        assertEquals("19:30", parsed.keyDates[0].time)
        assertEquals("21:00", parsed.keyDates[0].endTime)
    }

    @Test
    fun `tasks phrased as infinitives are not chatter`() {
        val reference = LocalDate.of(2026, 10, 13)
        assertTrue(SummaryResponseParser.isChatter(KeyDate("Anna odpowiedziała", "2026-10-13"), reference))
        assertTrue(SummaryResponseParser.isChatter(KeyDate("Ola asked about the bus"), reference))
        assertEquals(false, SummaryResponseParser.isChatter(KeyDate("Odpowiedzieć na ankietę", "2026-10-13"), reference))
        assertEquals(false, SummaryResponseParser.isChatter(KeyDate("Dodatkowy trening", "2026-10-13"), reference))
        assertEquals(false, SummaryResponseParser.isChatter(KeyDate("Zebranie", "2026-10-13"), reference))
    }
}

