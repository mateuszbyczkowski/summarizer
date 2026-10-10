package com.summarizer.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Date and time spans highlighted in a summary's overview. */
class SummaryDateSpansTest {

    private val reference = LocalDate.of(2026, 10, 13)

    private fun spans(text: String): List<String> =
        SummaryDates.findDateTimeSpans(text, reference).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `dates include a weekday before them`() {
        assertEquals(listOf("pt 17.10"), spans("W pt 17.10 klasa jedzie na wycieczkę."))
        assertEquals(listOf("śr 15.10"), spans("Wpłata 85 zł do śr 15.10."))
    }

    @Test
    fun `words that only start like a weekday are not part of the date`() {
        assertEquals(listOf("17.10"), spans("Wtedy 17.10 jedziemy."))
        assertEquals(listOf("17.10"), spans("Ponad 17.10 nic nie wiadomo."))
        assertEquals(listOf("sobotę 17.10"), spans("Wycieczka w sobotę 17.10."))
    }

    @Test
    fun `a time right after a date joins it`() {
        assertEquals(listOf("wt 21.10, 18:00"), spans("Zebranie rodziców wt 21.10, 18:00."))
        assertEquals(listOf("21.10 o 18:00"), spans("Zebranie 21.10 o 18:00 w szkole."))
    }

    @Test
    fun `times alone are marked without the word before them`() {
        assertEquals(listOf("7:30"), spans("Autobus odjeżdża wcześniej, o 7:30."))
        assertEquals(listOf("7.15"), spans("Zbiórka o 7.15 przy bramie."))
    }

    @Test
    fun `separate dates stay separate and amounts are not dates`() {
        assertEquals(listOf("15.10", "17.10"), spans("Wpłata do 15.10, wycieczka 17.10, koszt 2.10 zł."))
        assertEquals(listOf("17 października"), spans("Wycieczka 17 października."))
    }

    @Test
    fun `all times are found in order`() {
        val text = "Zbiórka 7:15, odjazd o 7:30, powrót 16:30."
        assertEquals(listOf("7:15", "7:30", "16:30"), SummaryDates.findTimes(text).map { text.substring(it.range.first, it.range.last + 1) })
    }

    @Test
    fun `text without dates has no spans`() {
        assertEquals(emptyList<String>(), spans("Dzieci mają zabrać drugie śniadanie."))
    }
}
