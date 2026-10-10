package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SummaryDatesTest {

    private val reference = LocalDate.of(2026, 10, 13)

    private fun firstDate(text: String, ref: LocalDate = reference) = SummaryDates.findDates(text, ref).firstOrNull()?.date

    @Test
    fun `numeric dates with and without a year`() {
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("17.10"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("zbiórka 17/10 rano"))
        assertEquals(LocalDate.of(2027, 10, 17), firstDate("17.10.2027"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("17.10.26"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("2026-10-17"))
        assertEquals(LocalDate.of(2026, 10, 5), firstDate("do 5.10."))
    }

    @Test
    fun `month names and weekdays`() {
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("17 października"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("pt 17.10"))
        assertEquals(LocalDate.of(2026, 11, 3), firstDate("3 lis"))
        assertEquals(LocalDate.of(2027, 1, 6), firstDate("6 stycznia"))
        assertEquals(LocalDate.of(2026, 12, 24), firstDate("24 grudnia 2026"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("Friday, October 17"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("17th Oct"))
    }

    @Test
    fun `relative words and invalid numbers stay unresolved`() {
        assertNull(firstDate("w środę"))
        assertNull(firstDate("jutro rano"))
        assertNull(firstDate("o 7.30"))
        assertNull(firstDate("85.50 zł"))
        assertNull(firstDate("31.02"))
        assertNull(firstDate("7:30"))
    }

    @Test
    fun `missing year is the nearest upcoming one, tolerating the recent past`() {
        // A deadline that passed shortly before the last message stays this year
        assertEquals(LocalDate.of(2026, 10, 1), firstDate("1.10"))
        // Early January seen in December is next year
        assertEquals(LocalDate.of(2027, 1, 5), firstDate("5.01", LocalDate.of(2026, 12, 20)))
        // Late December seen in early January is the one that just passed
        assertEquals(LocalDate.of(2025, 12, 20), firstDate("20.12", LocalDate.of(2026, 1, 5)))
        // 29 February resolves to the next leap year
        assertEquals(LocalDate.of(2028, 2, 29), firstDate("29.02", LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun `times with colon or after o and godz`() {
        assertEquals(LocalTime.of(7, 30), SummaryDates.findTime("17.10 7:30")?.time)
        assertEquals(LocalTime.of(18, 0), SummaryDates.findTime("zebranie o 18:00")?.time)
        assertEquals(LocalTime.of(7, 15), SummaryDates.findTime("zbiórka o 7.15")?.time)
        assertEquals(LocalTime.of(9, 5), SummaryDates.findTime("godz. 9.05")?.time)
        assertNull(SummaryDates.findTime("17.10"))
        assertNull(SummaryDates.findTime("do 17.10"))
    }

    @Test
    fun `key date lines`() {
        assertEquals(
            KeyDate("Wycieczka", "2026-10-17", "07:30", "autobus"),
            SummaryDates.parseKeyDate("17.10 7:30 | Wycieczka, autobus", reference)
        )
        assertEquals(
            KeyDate("Wycieczka", "2026-10-17", null, null),
            SummaryDates.parseKeyDate("pt 17.10 | Wycieczka", reference)
        )
        assertEquals(
            KeyDate("Zebranie", "2026-10-21", "18:00", null),
            SummaryDates.parseKeyDate("Zebranie – 21.10 o 18:00", reference)
        )
        assertEquals(
            KeyDate("Zebranie rodziców", "2026-10-21", "18:00", "sala 12"),
            SummaryDates.parseKeyDate("Zebranie rodziców 21.10 o 18:00, sala 12", reference)
        )
        assertEquals(
            KeyDate("Basen", null, "18:00", "w środę"),
            SummaryDates.parseKeyDate("w środę 18:00 | Basen", reference)
        )
        assertNull(SummaryDates.parseKeyDate("17.10 |", reference))
    }

    @Test
    fun `task deadlines`() {
        assertEquals(ActionItem("Przelew 85 zł", due = "2026-10-15"), SummaryDates.parseAction("Przelew 85 zł (do 15.10)", reference))
        assertEquals(ActionItem("Przelew 85 zł", due = "2026-10-15"), SummaryDates.parseAction("Przelew 85 zł (by Wed 15.10)", reference))
        assertEquals(ActionItem("Przelew 85 zł", due = "2026-10-15"), SummaryDates.parseAction("Przelew 85 zł do środy 15.10", reference))
        assertEquals(ActionItem("Oddać zgody", due = "2026-10-31"), SummaryDates.parseAction("Oddać zgody (do 31 października)", reference))
        // A date that belongs to the sentence stays in the text
        assertEquals(
            ActionItem("Marek zabiera Zosię na zbiórkę 17.10", due = "2026-10-17"),
            SummaryDates.parseAction("Marek zabiera Zosię na zbiórkę 17.10", reference)
        )
        assertEquals(
            ActionItem("Kasia zbiera zgody (Kasia, 20.10)", due = "2026-10-20"),
            SummaryDates.parseAction("Kasia zbiera zgody (Kasia, 20.10)", reference)
        )
        // The deadline wins over another date in the task
        assertEquals(
            ActionItem("Zapisać dziecko na wycieczkę 24.10", due = "2026-10-15"),
            SummaryDates.parseAction("Zapisać dziecko na wycieczkę 24.10 (do 15.10)", reference)
        )
        val unresolved = SummaryDates.parseAction("Oddać zgody (do poniedziałku)", reference)
        assertEquals("Oddać zgody (do poniedziałku)", unresolved.task)
        assertNull(unresolved.due)
        assertTrue(!unresolved.done)
    }

    @Test
    fun `amounts, quantities and names are not dates`() {
        assertNull(firstDate("Zapłacić \$2.10 za bilet"))
        assertNull(firstDate("Kupić 1.5 kg mąki"))
        assertNull(firstDate("Kupić 1.5 l mleka"))
        assertNull(firstDate("Kabel 2.5 m"))
        assertNull(firstDate("Drożdże 10.5 g."))
        assertNull(firstDate("Składka 2.10 zł"))
        assertNull(firstDate("Jan 2 bilety kupi"))
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("October 17"))
    }

    @Test
    fun `text next to the date is the event and a second date ends a range`() {
        val withPlace = SummaryDates.parseKeyDate("17.10 Wycieczka | Kopernik", reference)!!
        assertEquals("Wycieczka", withPlace.label)
        assertEquals("2026-10-17", withPlace.date)
        assertEquals("Kopernik", withPlace.detail)

        val range = SummaryDates.parseKeyDate("17.10 - 18.10 | Ferie", reference)!!
        assertEquals("Ferie", range.label)
        assertEquals("2026-10-17", range.date)
        assertEquals("– 18.10", range.detail)
    }

    @Test
    fun `prepositions and weekdays next to the date are not the event`() {
        assertEquals("Basen", SummaryDates.parseKeyDate("w piątek 17.10 | Basen", reference)!!.label)
        assertEquals("Przelew za wycieczkę", SummaryDates.parseKeyDate("Do 17.10 | Przelew za wycieczkę", reference)!!.label)
        val withTime = SummaryDates.parseKeyDate("17.10 g. 8:00 | Wycieczka", reference)!!
        assertEquals("2026-10-17", withTime.date)
        assertEquals("08:00", withTime.time)
        assertEquals(LocalDate.of(2026, 10, 17), firstDate("Oct 17"))
        assertEquals(LocalDate.of(2026, 12, 5), firstDate("Pay fee (due Dec 5)"))
    }
}

