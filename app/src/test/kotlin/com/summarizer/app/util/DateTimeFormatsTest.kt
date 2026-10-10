package com.summarizer.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DateTimeFormatsTest {

    // What android.text.format.DateFormat.getBestDateTimePattern returns for these skeletons
    private val englishPatterns = mapOf(
        "Hm" to "HH:mm",
        "hm" to "h:mm a",
        "MMMd" to "MMM d",
        "yMMMd" to "MMM d, y",
        "MMMEd" to "EEE, MMM d",
        "MEd" to "EEE, M/d",
        "EEE" to "ccc"
    )
    private val polishPatterns = mapOf(
        "Hm" to "HH:mm",
        "hm" to "h:mm a",
        "MMMd" to "d MMM",
        "yMMMd" to "d MMM y",
        "MMMEd" to "EEE, d MMM",
        "MEd" to "EEE, d.MM",
        "EEE" to "ccc"
    )

    private fun english(is24Hour: Boolean = false) =
        DateTimeFormats(Locale.US, is24Hour) { englishPatterns.getValue(it) }

    private fun polish(is24Hour: Boolean = true) =
        DateTimeFormats(Locale.forLanguageTag("pl-PL"), is24Hour) { polishPatterns.getValue(it) }

    private fun day(iso: String) = LocalDate.parse(iso)

    @Test
    fun `times follow the 12 or 24 hour setting`() {
        val time = LocalTime.of(17, 2)
        assertEquals("17:02", polish().time(time))
        assertEquals("17:02", english(is24Hour = true).time(time))
        assertEquals("5:02\u00A0PM", english().time(time))
        assertEquals("07:30", polish().time("07:30"))
        assertEquals("rano", polish().time("rano"))
    }

    @Test
    fun `dates use the locale's order and names`() {
        val friday = day("2026-10-16")
        assertEquals("Oct 16", english().shortDate(friday))
        assertEquals("16 paź", polish().shortDate(friday))
        assertEquals("Fri, Oct 16", english().weekdayDate(friday))
        assertEquals("pt., 16 paź", polish().weekdayDate(friday))
        assertEquals("Fri, 10/16", english().weekdayNumericDate(friday))
        assertEquals("pt., 16.10", polish().weekdayNumericDate(friday))
    }

    @Test
    fun `weekday badges are upper case without a trailing dot`() {
        val friday = day("2026-10-16")
        assertEquals("FRI", english().weekdayBadge(friday))
        assertEquals("PT", polish().weekdayBadge(friday))
        assertEquals("ŚR", polish().weekdayBadge(day("2026-10-14")))
        assertEquals("NIE", polish().weekdayBadge(day("2026-10-18"))) // "niedz." fits the badge
    }

    @Test
    fun `past dates show the year only when it differs`() {
        val today = day("2026-10-20")
        assertEquals(english().shortDate(day("2026-03-02")), english().pastDate(day("2026-03-02"), today))
        assertTrue(english().pastDate(day("2025-03-02"), today).contains("2025"))
        assertTrue(polish().pastDate(day("2025-03-02"), today).contains("2025"))
    }

    @Test
    fun `day ranges share the month on the locale's side`() {
        fun en(start: String, end: String) = english().dayRange(day(start), day(end))
        fun pl(start: String, end: String) = polish().dayRange(day(start), day(end))

        assertEquals("Oct 13", en("2026-10-13", "2026-10-13"))
        assertEquals("Oct 9–10", en("2026-10-09", "2026-10-10"))
        assertEquals("Sep 28 – Oct 3", en("2026-09-28", "2026-10-03"))
        assertEquals("Dec 28, 2025 – Jan 2, 2026", en("2025-12-28", "2026-01-02"))

        assertEquals("13 paź", pl("2026-10-13", "2026-10-13"))
        assertEquals("9–10 paź", pl("2026-10-09", "2026-10-10"))
        assertEquals("28 wrz – 3 paź", pl("2026-09-28", "2026-10-03"))
        assertEquals("28 gru 2025 – 2 sty 2026", pl("2025-12-28", "2026-01-02"))

        // Reversed input is tolerated
        assertEquals("9–10 paź", pl("2026-10-10", "2026-10-09"))
    }

    @Test
    fun `unknown pattern letters fall back to the localized style`() {
        val formats = DateTimeFormats(Locale.US, is24Hour = false) { "h:mm b" }
        val expected = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US).format(LocalTime.of(9, 5))
            .replace('\u202F', '\u00A0').replace(' ', '\u00A0')
        assertEquals(expected, formats.time(LocalTime.of(9, 5)))
    }
}
