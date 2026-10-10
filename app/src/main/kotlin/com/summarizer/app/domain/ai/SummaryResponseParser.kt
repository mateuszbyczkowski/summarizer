package com.summarizer.app.domain.ai

/**
 * Reads the OVERVIEW / TOPICS / ACTIONS / ANNOUNCEMENTS format requested by [PromptTemplate].
 *
 * Tolerates what small models commonly do: markdown around labels, content on the label line,
 * numbered or starred bullets, copied format hints, and "None" (in a few languages) for empty
 * sections. Without any labels, the whole reply is used as the overview.
 */
object SummaryResponseParser {

    data class ParsedSummary(
        val overview: String,
        val topics: List<String>,
        val actions: List<String>,
        val announcements: List<String>
    )

    private enum class Section { OVERVIEW, TOPICS, ACTIONS, ANNOUNCEMENTS }

    private const val MAX_ITEMS = 5
    private const val MAX_FALLBACK_OVERVIEW_CHARS = 400

    // Label with ":" and optional inline content, or a bare label on its own line ("OVERVIEW").
    // Prefix matching tolerates misspellings ("ANNOUNCEMTY"); Polish labels appear when a model
    // translates them despite the instruction.
    private val SECTION_LABEL = Regex(
        "^(?:\\d+[.)]\\s*)?(OVERVIEW|PRZEGL\\p{L}*|TOPIC\\p{L}*|TEMAT\\p{L}*|ACTION\\p{L}*|ZADANI\\p{L}*|DZIA\\p{L}*|ANNOUNC\\p{L}*|OG\\p{L}OSZ\\p{L}*)" +
            "\\s*(?::\\s*(.*)|-\\s+(.*))?$",
        RegexOption.IGNORE_CASE
    )
    private val PLACEHOLDER = Regex("^<.*>$")
    private val MARKDOWN = Regex("\\*\\*|__|^#+\\s*", RegexOption.MULTILINE)
    private val BULLET = Regex("^(?:[-*•–]|\\d+[.)])\\s*")
    private val EMPTY_MARKERS = setOf(
        "none", "none.", "n/a", "-", "...", "…", "nothing", "no", "brak", "brak.", "nic",
        "keine", "aucun", "aucune", "ninguno", "ninguna", "nessuno", "nenhum", "nenhuma"
    )
    private val FORMAT_HINTS = PromptTemplate.FORMAT_HINTS.map { it.lowercase() }.toSet()

    fun parse(rawResponse: String): ParsedSummary {
        val lines = rawResponse.replace(MARKDOWN, "").lines()
        val sections = mutableMapOf<Section, MutableList<String>>()
        var current: Section? = null

        for (line in lines) {
            val label = SECTION_LABEL.find(line.trim())
            if (label != null) {
                current = sectionFor(label.groupValues[1])
                val inline = label.groupValues[2].ifEmpty { label.groupValues[3] }
                val section = sections.getOrPut(current) { mutableListOf() }
                // "TOPICS: a, b, c" on one line is a list too
                if (current != Section.OVERVIEW && inline.count { it == ',' } >= 2) {
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
                overview = rawResponse.replace(MARKDOWN, "").trim().take(MAX_FALLBACK_OVERVIEW_CHARS),
                topics = emptyList(),
                actions = emptyList(),
                announcements = emptyList()
            )
        }

        return ParsedSummary(
            overview = items(sections[Section.OVERVIEW]).joinToString(" "),
            topics = listItems(sections[Section.TOPICS]),
            actions = listItems(sections[Section.ACTIONS]),
            announcements = listItems(sections[Section.ANNOUNCEMENTS])
        )
    }

    private fun sectionFor(label: String): Section {
        val upper = label.uppercase()
        return when {
            upper.startsWith("OVERVIEW") || upper.startsWith("PRZEGL") -> Section.OVERVIEW
            upper.startsWith("TOPIC") || upper.startsWith("TEMAT") -> Section.TOPICS
            upper.startsWith("ACTION") || upper.startsWith("ZADANI") || upper.startsWith("DZIA") -> Section.ACTIONS
            else -> Section.ANNOUNCEMENTS
        }
    }

    /** Bullets without a trailing period, so "Koszt." and "Koszt" count as the same item. */
    private fun listItems(lines: List<String>?): List<String> =
        items(lines).map { it.trimEnd('.').trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_ITEMS)

    private fun items(lines: List<String>?): List<String> =
        lines.orEmpty()
            .map { it.trim().replace(BULLET, "").trim() }
            .filter { item ->
                val lower = item.lowercase()
                item.isNotEmpty() && lower !in EMPTY_MARKERS && lower !in FORMAT_HINTS && !PLACEHOLDER.matches(item)
            }
            // Small models sometimes loop on the same bullet
            .distinctBy { it.lowercase() }
}
