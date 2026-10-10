package com.summarizer.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Dates and times written the way the phone's locale writes them: "Fri, Oct 17" / "pt., 17 paź",
 * "5:02 PM" / "17:02", day ranges "Oct 9–10" / "9–10 paź".
 *
 * Plain Kotlin so it can be unit-tested: [bestPattern] turns a skeleton ("MMMd") into the locale's
 * pattern ("MMM d" in English, "d MMM" in Polish). On Android it is
 * `android.text.format.DateFormat.getBestDateTimePattern` (see `DateTimeFormatsAndroid.kt`);
 * tests pass fixed patterns. [is24Hour] is the phone's 12/24-hour setting.
 */
class DateTimeFormats(
    val locale: Locale,
    val is24Hour: Boolean,
    private val bestPattern: (skeleton: String) -> String
) {
    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    private fun formatter(skeleton: String): DateTimeFormatter =
        formatters.getOrPut(skeleton) {
            try {
                DateTimeFormatter.ofPattern(bestPattern(skeleton), locale)
            } catch (e: IllegalArgumentException) {
                // An ICU pattern letter java.time does not know (e.g. "B", flexible day period)
                val fallback = if (skeleton == SKELETON_TIME_24 || skeleton == SKELETON_TIME_12) {
                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                } else {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                }
                fallback.withLocale(locale)
            }
        }

    /**
     * Time of day: "17:02", or "5:02 PM" when the phone uses the 12-hour clock. Spaces become
     * no-break spaces so "PM" never wraps onto its own line.
     */
    fun time(time: LocalTime): String =
        formatter(if (is24Hour) SKELETON_TIME_24 else SKELETON_TIME_12).format(time)
            .replace(' ', NO_BREAK_SPACE)
            .replace(NARROW_NO_BREAK_SPACE, NO_BREAK_SPACE)

    /** An "HH:mm" time from a summary in the phone's format; the text itself if it does not parse. */
    fun time(text: String): String =
        try {
            time(LocalTime.parse(text))
        } catch (e: DateTimeParseException) {
            text
        }

    /**
     * A summary's start time with its end, if any: "19:30–21:00" / "7:30 PM – 9:00 PM"; only
     * the start without an end.
     */
    fun timeRange(start: String, end: String?): String =
        if (end == null) time(start) else time(start) + (if (is24Hour) "–" else " – ") + time(end)

    /** Day and short month: "Oct 12" / "12 paź". */
    fun shortDate(date: LocalDate): String = formatter(SKELETON_DAY_MONTH).format(date)

    /** A past day: "Oct 12" this year, "Oct 12, 2025" before (summaries are kept for good). */
    fun pastDate(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) shortDate(date) else formatter(SKELETON_DAY_MONTH_YEAR).format(date)

    /** Weekday, day and short month: "Fri, Oct 17" / "pt., 17 paź". Agenda rows, day headers, due dates. */
    fun weekdayDate(date: LocalDate): String = formatter(SKELETON_WEEKDAY_DAY_MONTH).format(date)

    /** Weekday with a numeric date, for text copied into chats: "Fri, 10/17" / "pt., 17.10". */
    fun weekdayNumericDate(date: LocalDate): String = formatter(SKELETON_WEEKDAY_NUMERIC).format(date)

    /** Short weekday name as the locale writes it: "Fri" / "pt.". */
    fun weekday(date: LocalDate): String = formatter(SKELETON_WEEKDAY).format(date)

    /**
     * Short weekday for date badges, upper case without a trailing dot and at most 3 letters, so
     * it fits a 36 dp badge: "FRI" / "PT", and "NIE" for Polish "niedz.".
     */
    fun weekdayBadge(date: LocalDate): String =
        weekday(date).trimEnd('.').take(MAX_BADGE_LETTERS).uppercase(locale)

    /**
     * A day range, compact: "Oct 13", "Oct 9–13", "Sep 28 – Oct 3", "Dec 28, 2025 – Jan 2, 2026"
     * in English; "13 paź", "9–13 paź", "28 wrz – 3 paź" in Polish. Within one month the day
     * numbers share the month, on whichever side the locale writes it.
     */
    fun dayRange(start: LocalDate, end: LocalDate): String {
        val (first, last) = if (end < start) end to start else start to end
        return when {
            first == last -> shortDate(first)
            first.year != last.year -> {
                val withYear = formatter(SKELETON_DAY_MONTH_YEAR)
                "${withYear.format(first)} – ${withYear.format(last)}"
            }
            first.month == last.month -> sameMonthRange(first, last)
            else -> "${shortDate(first)} – ${shortDate(last)}"
        }
    }

    private fun sameMonthRange(first: LocalDate, last: LocalDate): String {
        val pattern = bestPattern(SKELETON_DAY_MONTH)
        val dayIndex = pattern.indexOf('d')
        val monthIndex = pattern.indexOfFirst { it == 'M' || it == 'L' }
        val formattedLast = shortDate(last)
        val formattedFirst = shortDate(first)
        val firstDay = first.dayOfMonth.toString()
        val lastDay = last.dayOfMonth.toString()
        return when {
            // "d MMM": "9–13 paź"
            dayIndex in 0 until monthIndex && formattedLast.startsWith(lastDay) ->
                "$firstDay–$formattedLast"
            // "MMM d": "Oct 9–13"
            monthIndex in 0 until dayIndex && formattedFirst.endsWith(firstDay) ->
                "$formattedFirst–$lastDay"
            else -> "$formattedFirst – $formattedLast"
        }
    }

    companion object {
        private const val NO_BREAK_SPACE = '\u00A0'
        private const val NARROW_NO_BREAK_SPACE = '\u202F'
        const val SKELETON_TIME_24 = "Hm"
        const val SKELETON_TIME_12 = "hm"
        const val SKELETON_DAY_MONTH = "MMMd"
        const val SKELETON_DAY_MONTH_YEAR = "yMMMd"
        const val SKELETON_WEEKDAY_DAY_MONTH = "MMMEd"
        const val SKELETON_WEEKDAY_NUMERIC = "MEd"
        const val SKELETON_WEEKDAY = "EEE"
        private const val MAX_BADGE_LETTERS = 3
    }
}
