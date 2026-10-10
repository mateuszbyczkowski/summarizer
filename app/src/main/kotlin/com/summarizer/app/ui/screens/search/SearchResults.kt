package com.summarizer.app.ui.screens.search

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary

/** The kinds of results the filter chips choose between. */
enum class SearchResultType { SUMMARIES, TASKS, MESSAGES }

/** Active filter chips: a result type and/or one chat; null means "all". */
data class SearchFilter(
    val type: SearchResultType? = null,
    val threadId: String? = null
)

/** A task from a summary whose text (or assignee) matches the query. */
data class TaskHit(val summary: Summary, val index: Int, val item: ActionItem)

/** A chat that appears in the results, for the per-chat filter chips. */
data class SearchChat(val threadId: String, val threadName: String)

/** Builds and filters search results; free of Android and Compose so it can be unit tested. */
object SearchResultsLogic {

    /** Tasks of [summaries] that mention [query] (case-insensitive), in summary order. */
    fun taskHits(summaries: List<Summary>, query: String): List<TaskHit> =
        summaries.flatMap { summary ->
            summary.actionItems.mapIndexedNotNull { index, item ->
                val matches = item.task.contains(query, ignoreCase = true) ||
                    item.assignedTo?.contains(query, ignoreCase = true) == true
                if (matches) TaskHit(summary, index, item) else null
            }
        }

    /** Every chat with a summary or message result, in the order they first appear. */
    fun chats(summaries: List<Summary>, messages: List<Message>): List<SearchChat> =
        (summaries.map { SearchChat(it.threadId, it.threadName) } + messages.map { SearchChat(it.threadId, it.threadName) })
            .distinctBy { it.threadId }

    /**
     * Results limited to [filter]. A chat filter for a chat that is not among the results is
     * ignored, so a stale chip never hides everything.
     */
    fun apply(
        summaries: List<Summary>,
        tasks: List<TaskHit>,
        messages: List<Message>,
        filter: SearchFilter
    ): Triple<List<Summary>, List<TaskHit>, List<Message>> {
        val chatIds = chats(summaries, messages).map { it.threadId }.toSet()
        val threadId = filter.threadId?.takeIf { it in chatIds }
        fun show(type: SearchResultType) = filter.type == null || filter.type == type
        return Triple(
            if (show(SearchResultType.SUMMARIES)) summaries.filter { threadId == null || it.threadId == threadId } else emptyList(),
            if (show(SearchResultType.TASKS)) tasks.filter { threadId == null || it.summary.threadId == threadId } else emptyList(),
            if (show(SearchResultType.MESSAGES)) messages.filter { threadId == null || it.threadId == threadId } else emptyList()
        )
    }
}

/** Finding and trimming matched text for result rows. */
object SearchText {

    /** Non-overlapping, case-insensitive occurrences of [query] in [text]. */
    fun matchRanges(text: String, query: String): List<IntRange> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (from <= text.length - needle.length) {
            val index = text.indexOf(needle, startIndex = from, ignoreCase = true)
            if (index < 0) break
            ranges += index until index + needle.length
            from = index + needle.length
        }
        return ranges
    }

    /**
     * Up to about [maxLength] characters of [text] around the first match, on one line, with "…"
     * where it was cut. Text without a match is cut from the start.
     */
    fun snippet(text: String, query: String, maxLength: Int = 140): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= maxLength) return flat
        val match = matchRanges(flat, query).firstOrNull()
        var start = if (match == null) 0 else (match.first - maxLength / 3).coerceAtLeast(0)
        // Start at a word boundary when cutting inside the text
        if (start > 0) {
            val space = flat.indexOf(' ', start)
            if (space in start until (match?.first ?: flat.length)) start = space + 1
        }
        val end = (start + maxLength).coerceAtMost(flat.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < flat.length) "…" else ""
        return prefix + flat.substring(start, end).trim() + suffix
    }

    /**
     * The part of a summary worth showing for [query]: the first field that mentions it
     * (overview, topics, announcements, dates), else the overview or the topics.
     */
    fun summarySnippet(summary: Summary, query: String): String {
        val fields = buildList {
            add(summary.overview)
            addAll(summary.keyTopics)
            addAll(summary.announcements)
            addAll(summary.keyDates.map { listOfNotNull(it.label, it.detail).joinToString(" · ") })
        }.filter { it.isNotBlank() }
        val source = fields.firstOrNull { it.contains(query.trim(), ignoreCase = true) }
            ?: summary.overview.takeIf { it.isNotBlank() }
            ?: summary.keyTopics.joinToString(" · ")
        return snippet(source, query)
    }
}
