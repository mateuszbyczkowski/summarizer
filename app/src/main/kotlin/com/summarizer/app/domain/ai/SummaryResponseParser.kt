package com.summarizer.app.domain.ai

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import java.time.LocalDate

/**
 * Reads the OVERVIEW / TOPICS / ACTIONS / ANNOUNCEMENTS / DATES format requested by [PromptTemplate].
 *
 * Tolerates what small models commonly do: markdown around labels, content on the label line,
 * numbered or starred bullets, copied format hints, and "None" (in a few languages) for empty
 * sections. Without any labels, the whole reply is used as the overview. Deadlines in actions
 * and DATES lines are resolved with [SummaryDates].
 */
object SummaryResponseParser {

    data class ParsedSummary(
        val overview: String,
        val topics: List<String>,
        val actions: List<ActionItem>,
        val announcements: List<String>,
        val keyDates: List<KeyDate> = emptyList()
    )

    private enum class Section { OVERVIEW, TOPICS, ACTIONS, ANNOUNCEMENTS, DATES }

    private const val MAX_ITEMS = 5
    private const val MAX_FALLBACK_OVERVIEW_CHARS = 400

    // A label at the start of a line, with ":" or "-" and optional inline content, or alone on
    // its line ("OVERVIEW"). English labels tolerate typos ("ANNOUNCEMTY") and variants ("Key
    // Topics", "Action items"); Polish labels (used when a model translates them) must be whole
    // words so content such as "Dziadek: odbierze Zosię" is not taken for a label.
    private val SECTION_LABEL = Regex(
        "^(?:\\d+[.)]\\s*)?" +
            "((?:(?:KEY|IMPORTANT)\\s+)?(?:OVERVIEW|SUMMARY|TOPIC\\p{L}*|ACTION\\p{L}*(?:\\s+ITEMS?)?|ANNOUNC\\p{L}*|DATES)" +
            "|PRZEGLĄD|PODSUMOWANIE|TEMATY|DZIAŁANIA|ZADANIA|OGŁOSZENIA|(?:WAŻNE\\s+)?DATY|TERMINY)" +
            "\\s*(?::\\s*(.*)|-\\s+(.*))?$",
        RegexOption.IGNORE_CASE
    )
    private val PLACEHOLDER = Regex("^[|\\s-]*<.*>[|\\s-]*$")
    // Date placeholders from the format a model may copy into a real item ("Przelew (by DD.MM)")
    private val TEMPLATE_FRAGMENT = Regex("\\s*\\(?(?:by\\s+)?DD\\.MM\\)?|\\s*HH:MM", RegexOption.IGNORE_CASE)
    private val MARKDOWN = Regex("\\*\\*|__|^#+\\s*", RegexOption.MULTILINE)
    // "- ", "* ", "• " or a list number followed by a space ("1. "); "17.10" is a date, not a number
    private val WHITESPACE_RUN = Regex("\\s+")
    private val BULLET = Regex("^(?:[-*•–]\\s*|\\d{1,2}[.)]\\s+)")
    private val EMPTY_MARKERS = setOf(
        "none", "none.", "n/a", "-", "...", "…", "nothing", "no", "brak", "brak.", "nic",
        "keine", "aucun", "aucune", "ninguno", "ninguna", "nessuno", "nenhum", "nenhuma"
    )
    private val FORMAT_HINTS = PromptTemplate.FORMAT_HINTS.map { it.lowercase() }.toSet()

    /**
     * Parses a reply.
     *
     * @param reference Day the summarized conversation ended; dates without a year are resolved
     *        relative to it
     */
    fun parse(rawResponse: String, reference: LocalDate = LocalDate.now()): ParsedSummary {
        val lines = rawResponse.replace(MARKDOWN, "").lines()
        val sections = mutableMapOf<Section, MutableList<String>>()
        var current: Section? = null

        for (line in lines) {
            val label = SECTION_LABEL.find(line.trim())
            if (label != null) {
                current = sectionFor(label.groupValues[1])
                val inline = label.groupValues[2].ifEmpty { label.groupValues[3] }
                val section = sections.getOrPut(current) { mutableListOf() }
                // "TOPICS: a, b, c" on one line is a list too (date lines use commas for details)
                if (current != Section.OVERVIEW && current != Section.DATES && inline.count { it == ',' } >= 2) {
                    section.addAll(inline.split(','))
                } else {
                    section.add(inline)
                }
            } else if (current != null) {
                sections.getValue(current).add(line)
            }
        }

        if (sections.isEmpty()) {
            return ParsedSummary(
                overview = truncateAtWord(rawResponse.replace(MARKDOWN, "").trim(), MAX_FALLBACK_OVERVIEW_CHARS),
                topics = emptyList(),
                actions = emptyList(),
                announcements = emptyList()
            )
        }

        return ParsedSummary(
            overview = items(sections[Section.OVERVIEW]).joinToString(" "),
            topics = listItems(sections[Section.TOPICS]),
            actions = listItems(sections[Section.ACTIONS]).map { SummaryDates.parseAction(it, reference) },
            announcements = listItems(sections[Section.ANNOUNCEMENTS]),
            keyDates = listItems(sections[Section.DATES]).mapNotNull { SummaryDates.parseKeyDate(it, reference) }
        )
    }

    /**
     * The overview of a reply that is still being written, for showing progress. Empty while
     * the model is still writing the OVERVIEW label; the whole text if it ignores the format.
     */
    fun streamingOverview(partial: String): String {
        // Called for every streamed chunk on the engine thread: only look at the overview
        // section, without the full parse (date resolution etc.)
        val lines = partial.replace(MARKDOWN, "").lines()
        var inOverview = false
        var sawLabel = false
        val overview = StringBuilder()
        for (line in lines) {
            val label = SECTION_LABEL.find(line.trim())
            if (label != null) {
                sawLabel = true
                if (inOverview) break
                inOverview = sectionFor(label.groupValues[1]) == Section.OVERVIEW
                if (inOverview) overview.append(label.groupValues[2].ifEmpty { label.groupValues[3] })
            } else if (inOverview) {
                overview.append(' ').append(line.trim().replace(BULLET, ""))
            }
        }
        if (sawLabel) return overview.toString().replace(WHITESPACE_RUN, " ").trim()
        // The reply may start with an unfinished label ("OVER")
        val text = partial.replace(MARKDOWN, "").trim()
        return if ("OVERVIEW".startsWith(text.trim { !it.isLetter() }, ignoreCase = true)) "" else text
    }

    private fun truncateAtWord(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }

    private fun sectionFor(label: String): Section {
        val upper = label.uppercase().removePrefix("KEY").removePrefix("IMPORTANT").trim()
        return when {
            upper.startsWith("OVERVIEW") || upper.startsWith("SUMMARY") ||
                upper == "PRZEGLĄD" || upper == "PODSUMOWANIE" -> Section.OVERVIEW
            upper.startsWith("TOPIC") || upper == "TEMATY" -> Section.TOPICS
            upper.startsWith("ACTION") || upper == "DZIAŁANIA" || upper == "ZADANIA" -> Section.ACTIONS
            upper.endsWith("DATES") || upper.endsWith("DATY") || upper == "TERMINY" -> Section.DATES
            else -> Section.ANNOUNCEMENTS
        }
    }

    /** Bullets without a trailing period, so "Koszt." and "Koszt" count as the same item. */
    private fun listItems(lines: List<String>?): List<String> =
        items(lines).map { it.trimEnd('.').trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_ITEMS)

    private fun items(lines: List<String>?): List<String> =
        lines.orEmpty()
            .map { it.trim().replace(BULLET, "").trim() }
            .filter { it.lowercase() !in FORMAT_HINTS }
            .map { it.replace(TEMPLATE_FRAGMENT, "").trim() }
            .filter { item ->
                item.isNotEmpty() && item.lowercase() !in EMPTY_MARKERS && !PLACEHOLDER.matches(item)
            }
            // Small models sometimes loop on the same bullet
            .distinctBy { it.lowercase() }
}
