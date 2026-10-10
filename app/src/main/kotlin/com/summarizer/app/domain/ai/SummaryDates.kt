package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Finds dates and times in summary lines written by a model: numeric ("17.10", "17.10.2026",
 * "2026-10-17"), Polish and English month names ("17 października", "Oct 17"), optionally with
 * a weekday ("pt 17.10"). Relative words ("w środę", "jutro") stay unresolved.
 *
 * Dates without a year get the nearest upcoming one relative to a reference day (the summary's
 * last message), tolerating dates a little in the past such as a deadline that just passed.
 */
object SummaryDates {

    data class DateMatch(val range: IntRange, val date: LocalDate)

    data class TimeMatch(val range: IntRange, val time: LocalTime)

    /** Dates up to this many days before the reference stay in the past instead of moving to next year. */
    private const val PAST_TOLERANCE_DAYS = 30L

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    private val ISO_DATE = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")

    // "17.10", "17/10", "17.10.2026", "17.10.26"; not part of a longer number, time, decimal or
    // amount ("$2.10", "1.5 kg", "2.10 zł")
    private val NUMERIC_DATE = Regex(
        "(?<![\\d.,:/$€£])(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{4}|\\d{2}))?(?![\\d:/]|[.,]\\d)" +
            "(?!\\s*(?:zł|zl|pln|eur|usd|kg|km|ml|mg|%|\\$|€)(?!\\p{L}))",
        RegexOption.IGNORE_CASE
    )

    // "17 października", "17 paź", "17th October 2026"
    private val DAY_MONTH_NAME = Regex(
        "(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\.?\\s*(\\p{L}{3,})\\.?(?:\\s+(\\d{4}))?(?![\\p{L}\\d])",
        RegexOption.IGNORE_CASE
    )

    // "October 17", "October 17, 2026" (full English names only: "Jan 2" is also a Polish name)
    private val MONTH_NAME_DAY = Regex(
        "(?<!\\p{L})(\\p{L}{3,})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?(?![\\p{L}\\d])",
        RegexOption.IGNORE_CASE
    )

    // "7:30" with an optional "o"/"at"/"godz." before it, or "o 7.30" (a dot only after such a word)
    private val TIME_COLON = Regex(
        "(?:(?<!\\p{L})(?:o|at|godz\\.?|godzinie|g\\.)\\s*)?(?<![\\d.:])([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)",
        RegexOption.IGNORE_CASE
    )
    private val TIME_DOT = Regex(
        "(?<!\\p{L})(?:o|at|godz\\.?|godzinie|g\\.)\\s*([01]?\\d|2[0-3])\\.([0-5]\\d)(?![\\d.])",
        RegexOption.IGNORE_CASE
    )

    private val WEEKDAY = "(?:pon\\p{L}*|wt\\p{L}*|śr\\p{L}*|sr\\p{L}*|czw\\p{L}*|pt|piąt\\p{L}*|piat\\p{L}*|sob\\p{L}*|nd|niedz\\p{L}*|" +
        "mon\\p{L}*|tue\\p{L}*|wed\\p{L}*|thu\\p{L}*|fri\\p{L}*|sat\\p{L}*|sun\\p{L}*)\\.?,?"
    private val DEADLINE_WORD = "(?:do|by|due|until|before|przed|termin|deadline)"

    /** What may surround a deadline date for it to be dropped from the task text. */
    private val DEADLINE_FILLER = Regex("^[\\s:,]*(?:$DEADLINE_WORD(?!\\p{L}))?[\\s:,]*(?:$WEEKDAY)?[\\s:,.]*$", RegexOption.IGNORE_CASE)
    private val DEADLINE_BEFORE = Regex("(?<!\\p{L})$DEADLINE_WORD\\s*:?\\s*(?:$WEEKDAY\\s*)?$", RegexOption.IGNORE_CASE)
    private val TRAILING_PARENS = Regex("\\s*[(\\[]([^()\\[\\]]*)[)\\]]\\s*\\.?\\s*$")
    private val WHEN_FILLER = Regex(
        "^\\s*(?:(?:w|we|do|od|na|by|on|at|until|o|godz\\.?|g\\.)(?!\\p{L})\\s*)*(?:$WEEKDAY)?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val WEEKDAY_ONLY = Regex("^\\s*(?:$WEEKDAY\\s*)*$", RegexOption.IGNORE_CASE)
    private val SEPARATOR = Regex("\\s+[-–—]\\s+|\\s*\\|\\s*")
    private val WEEKDAY_BEFORE = Regex("(?<!\\p{L})$WEEKDAY\\s*$", RegexOption.IGNORE_CASE)
    /** What may stand between a date and its time for both to be one span: "21.10, 18:00", "21.10 o 18:00". */
    private val SPAN_JOINER = Regex("^[\\s,]*(?:(?:o|at|godz\\.?|g\\.)\\s*)?$", RegexOption.IGNORE_CASE)
    private val EDGE_PUNCTUATION = " \t-–—:|,;."
    private val WHITESPACE = Regex("\\s+")

    private val MONTHS: Map<String, Int> = buildMap {
        fun add(month: Int, vararg names: String) = names.forEach { put(it, month) }
        add(1, "stycznia", "styczeń", "styczen", "sty", "january", "jan")
        add(2, "lutego", "luty", "lut", "february", "feb")
        add(3, "marca", "marzec", "mar", "march")
        add(4, "kwietnia", "kwiecień", "kwiecien", "kwi", "april", "apr")
        add(5, "maja", "maj", "may")
        add(6, "czerwca", "czerwiec", "cze", "june", "jun")
        add(7, "lipca", "lipiec", "lip", "july", "jul")
        add(8, "sierpnia", "sierpień", "sierpien", "sie", "august", "aug")
        add(9, "września", "wrzesnia", "wrzesień", "wrzesien", "wrz", "september", "sept", "sep")
        add(10, "października", "pazdziernika", "październik", "pazdziernik", "paź", "paz", "october", "oct")
        add(11, "listopada", "listopad", "lis", "november", "nov")
        add(12, "grudnia", "grudzień", "grudzien", "gru", "december", "dec")
    }
    /** Month names allowed before the day ("Oct 17"); "Jan" and "Mar" are left out (a name and a Polish word). */
    private val ENGLISH_MONTHS = setOf(
        "january", "february", "feb", "march", "april", "apr", "may", "june", "jun", "july", "jul",
        "august", "aug", "september", "sept", "sep", "october", "oct", "november", "nov", "december", "dec"
    )

    /** All dates in [text] that resolve to a real day, in reading order. */
    fun findDates(text: String, reference: LocalDate): List<DateMatch> {
        val found = mutableListOf<DateMatch>()
        ISO_DATE.findAll(text).forEach { m ->
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                ?.let { found += DateMatch(m.range, it) }
        }
        NUMERIC_DATE.findAll(text).forEach { m ->
            resolve(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], reference)
                ?.let { found += DateMatch(m.range, it) }
        }
        DAY_MONTH_NAME.findAll(text).forEach { m ->
            val month = MONTHS[m.groupValues[2].lowercase()] ?: return@forEach
            resolve(m.groupValues[1].toInt(), month, m.groupValues[3], reference)?.let { found += DateMatch(m.range, it) }
        }
        MONTH_NAME_DAY.findAll(text).forEach { m ->
            val name = m.groupValues[1].lowercase()
            if (name !in ENGLISH_MONTHS) return@forEach
            resolve(m.groupValues[2].toInt(), MONTHS.getValue(name), m.groupValues[3], reference)
                ?.let { found += DateMatch(m.range, it) }
        }
        // Patterns can overlap ("17 października 2026" vs "2026"); keep the earliest, longest match
        return found.sortedWith(compareBy({ it.range.first }, { -it.range.last }))
            .fold(mutableListOf()) { kept, match ->
                if (kept.none { it.range.last >= match.range.first }) kept += match
                kept
            }
    }

    /** The first time of day in [text]; the range includes a leading "o"/"at"/"godz.". */
    fun findTime(text: String): TimeMatch? =
        listOfNotNull(TIME_COLON.find(text), TIME_DOT.find(text))
            .minByOrNull { it.range.first }
            ?.let { m -> TimeMatch(m.range, LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())) }

    /** Every time of day in [text], in reading order; ranges cover only the time ("7:30"), not "o"/"at". */
    fun findTimes(text: String): List<TimeMatch> =
        (TIME_COLON.findAll(text) + TIME_DOT.findAll(text))
            .map { m ->
                val digits = m.groups[1]!!.range
                TimeMatch(digits.first..m.range.last, LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()))
            }
            .sortedBy { it.range.first }
            .toList()

    /**
     * Spans of [text] that name a date or time, for highlighting: a weekday before a date is
     * included ("pt 17.10") and a date followed by its time is one span ("wt 21.10, 18:00").
     */
    fun findDateTimeSpans(text: String, reference: LocalDate): List<IntRange> {
        val dates = findDates(text, reference).map { match ->
            val weekday = WEEKDAY_BEFORE.find(text.substring(0, match.range.first))
            // "17 października." keeps its full stop out of the mark
            val end = if (text[match.range.last] == '.') match.range.last - 1 else match.range.last
            (weekday?.range?.first ?: match.range.first)..end
        }
        val times = findTimes(text).map { it.range }
            .filter { time -> dates.none { time.first <= it.last && it.first <= time.last } }
        val spans = dates.toMutableList()
        times.forEach { time ->
            // A time right after a date belongs to it
            val index = spans.indexOfFirst { it.last < time.first && SPAN_JOINER.matches(text.substring(it.last + 1, time.first)) }
            if (index >= 0 && spans[index] in dates) spans[index] = spans[index].first..time.last else spans += time
        }
        return spans.sortedBy { it.first }
    }

    /**
     * A DATES line such as "17.10 7:30 | Wycieczka, autobus": the part with the date and time,
     * then what happens; text after the first comma becomes the detail (usually the place).
     */
    fun parseKeyDate(line: String, reference: LocalDate): KeyDate? {
        val parts = line.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        // "when | what": the first part naming a date or time is the "when"; without one, the part
        // before an explicit "|" ("w środę | Basen"), else the whole line is the event
        val whenIndex = parts.indexOfFirst { findDates(it, reference).isNotEmpty() || findTime(it) != null }
            .takeIf { it >= 0 } ?: if ('|' in line && parts.size > 1) 0 else -1
        val whenText = if (whenIndex >= 0) parts[whenIndex] else line
        // A further part that is only a date is the end of a range ("17.10 - 18.10 | Ferie")
        val rangeEnds = parts.filterIndexed { i, part -> i != whenIndex && isOnlyDate(part, reference) }
        val whatParts = parts.filterIndexed { i, part -> i != whenIndex && part !in rangeEnds }
        val whatText = if (whenIndex >= 0 && whatParts.isNotEmpty()) whatParts.joinToString(", ") else null

        val date = findDates(whenText, reference).firstOrNull()
        val withoutDate = whenText.blankOut(date?.range)
        val time = findTime(withoutDate)
        val rest = withoutDate.blankOut(time?.range).cleanup()
        // Next to a resolved date, leftovers like "w piątek" or "do" are not an event name; without
        // one, "w środę" is the only "when" there is and stays as the detail
        val meaningfulRest = rest.takeIf {
            it.isNotEmpty() && !WEEKDAY_ONLY.matches(it) && !(date != null && WHEN_FILLER.matches(it))
        }

        // Text next to a resolved date is the event ("17.10 Wycieczka | Kopernik": event
        // "Wycieczka", detail "Kopernik"); next to an unresolved one ("w środę | Basen") it is
        // kept as the detail
        val description: String
        val extraDetail: String?
        when {
            whatText == null -> { description = rest; extraDetail = null }
            meaningfulRest != null && date != null -> { description = meaningfulRest; extraDetail = whatText }
            date == null -> { description = whatText; extraDetail = meaningfulRest }
            else -> { description = whatText; extraDetail = null }
        }
        val label = description.substringBefore(',').cleanup()
        val place = description.substringAfter(',', "").cleanup()
        if (label.isEmpty()) return null
        val rangeEnd = rangeEnds.firstOrNull()?.let { "– $it" }

        return KeyDate(
            label = label,
            date = date?.date?.toString(),
            time = time?.time?.format(TIME_FORMAT),
            detail = listOfNotNull(rangeEnd, extraDetail, place.ifEmpty { null }).joinToString(", ").ifEmpty { null }
        )
    }

    /**
     * A task with its deadline: "Przelew 85 zł (do 15.10)" becomes task "Przelew 85 zł", due
     * 15.10. A date that is part of the sentence stays in the text.
     */
    fun parseAction(text: String, reference: LocalDate): ActionItem {
        val dates = findDates(text, reference)
        if (dates.isEmpty()) return ActionItem(task = text)

        val parens = TRAILING_PARENS.find(text)
        val inParens = parens?.let { p -> dates.lastOrNull { it.range.first >= p.range.first } }
        val afterDeadlineWord = dates.firstOrNull { DEADLINE_BEFORE.containsMatchIn(text.substring(0, it.range.first)) }
        val due = inParens ?: afterDeadlineWord ?: dates.first()

        var task = text
        if (parens != null && due === inParens) {
            // Drop "(do pt 15.10)" but keep "(Kasia, 15.10)"
            val content = parens.groups[1]!!
            val withoutDate = text.blankOut(due.range).substring(content.range.first, content.range.last + 1)
            if (DEADLINE_FILLER.matches(withoutDate)) task = text.substring(0, parens.range.first)
        } else if (text.substring(due.range.last + 1).trim().trimEnd('.').isEmpty()) {
            // Trailing "do 15.10" / "by Fri 17.10"
            val before = text.substring(0, due.range.first)
            DEADLINE_BEFORE.find(before)?.let { task = before.substring(0, it.range.first) }
        }
        return ActionItem(task = task.cleanup().ifEmpty { text }, due = due.date.toString())
    }

    private fun isOnlyDate(text: String, reference: LocalDate): Boolean {
        val date = findDates(text, reference).firstOrNull() ?: return false
        return text.blankOut(date.range).let { rest -> rest.blankOut(findTime(rest)?.range) }.cleanup().isEmpty()
    }

    private fun resolve(day: Int, month: Int, yearText: String, reference: LocalDate): LocalDate? {
        if (yearText.isNotEmpty()) {
            val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
            return date(year, month, day)
        }
        // Nearest upcoming date, allowing a little of the past
        val earliest = reference.minusDays(PAST_TOLERANCE_DAYS)
        // Up to four years ahead, so 29.02 finds the next leap year
        return (reference.year - 1..reference.year + 4)
            .mapNotNull { date(it, month, day) }
            .firstOrNull { !it.isBefore(earliest) }
    }

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }

    /** Replaces [range] with spaces, so the words around it do not run together and indices stay valid. */
    private fun String.blankOut(range: IntRange?): String =
        if (range == null) this else replaceRange(range, " ".repeat(range.last - range.first + 1))

    private fun String.cleanup(): String =
        replace(WHITESPACE, " ").replace(" ,", ",").trim { it in EDGE_PUNCTUATION }.trim()
}
