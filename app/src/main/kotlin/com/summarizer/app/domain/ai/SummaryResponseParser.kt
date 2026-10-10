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

    private val SECTION_LABEL = Regex(
        "^(OVERVIEW|TOPICS|ACTIONS|ANNOUNCEMENTS)\\s*:\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )
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
                current = Section.valueOf(label.groupValues[1].uppercase())
                sections.getOrPut(current) { mutableListOf() }.add(label.groupValues[2])
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
            topics = items(sections[Section.TOPICS]).take(MAX_ITEMS),
            actions = items(sections[Section.ACTIONS]).take(MAX_ITEMS),
            announcements = items(sections[Section.ANNOUNCEMENTS]).take(MAX_ITEMS)
        )
    }

    private fun items(lines: List<String>?): List<String> =
        lines.orEmpty()
            .map { it.trim().replace(BULLET, "").trim() }
            .filter { item ->
                val lower = item.lowercase()
                item.isNotEmpty() && lower !in EMPTY_MARKERS && lower !in FORMAT_HINTS
            }
}
