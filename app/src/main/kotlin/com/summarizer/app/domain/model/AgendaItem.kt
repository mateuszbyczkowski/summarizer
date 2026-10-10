package com.summarizer.app.domain.model

/**
 * An upcoming date or a task from the latest summary of a followed thread.
 *
 * @property index Position in the summary's [Summary.keyDates] (DATE) or [Summary.actionItems] (TASK)
 * @property date ISO date (yyyy-MM-dd), or null when the summary gives none
 * @property time HH:mm, for dates that name a time
 * @property done For tasks: ticked off by the user; always false for dates
 */
data class AgendaItem(
    val threadId: String,
    val threadName: String,
    val summaryId: Long,
    val kind: Kind,
    val label: String,
    val date: String?,
    val time: String?,
    val index: Int,
    val done: Boolean
) {
    enum class Kind { DATE, TASK }
}
