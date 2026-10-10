package com.summarizer.app.ui.widget

import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.usecase.AgendaSchedule
import java.time.LocalDate

/** One line of the widget: a date or a task of the coming week. */
data class UpcomingWidgetRow(
    val threadId: String,
    val threadName: String,
    val label: String,
    val kind: AgendaItem.Kind,
    val date: LocalDate,
    val time: String?,
    val urgent: Boolean,
    val endTime: String? = null
)

/** What the "Upcoming" widget shows. */
sealed interface UpcomingWidgetState {

    /**
     * The app lock is on: anyone can see the home screen, so only how many things are coming
     * up, without labels or chat names.
     */
    data class CountOnly(val count: Int) : UpcomingWidgetState

    /** The app lock is off: the first [rows] of the [total] things coming up. */
    data class Details(val rows: List<UpcomingWidgetRow>, val total: Int) : UpcomingWidgetState

    /** The data could not be read (e.g. the database could not be opened). */
    data object Unavailable : UpcomingWidgetState
}

/**
 * Pure decisions behind the widget, kept free of Android so they can be unit tested. Uses the
 * same rules as the home screen's "This week" card ([AgendaSchedule.isThisWeek]).
 */
object UpcomingWidgetModel {

    /** Most rows the widget shows, however tall it is. */
    const val MAX_ROWS = 4

    /** Vertical padding and the header, in dp. */
    const val CHROME_HEIGHT_DP = 56

    /** Height of one row (a 48dp touch target), in dp. */
    const val ROW_PITCH_DP = 48

    fun build(agenda: List<AgendaItem>, today: LocalDate, appLockEnabled: Boolean): UpcomingWidgetState {
        val week = agenda.filter { AgendaSchedule.isThisWeek(it, today) }
        if (appLockEnabled) return UpcomingWidgetState.CountOnly(week.size)
        val rows = week.asSequence()
            .mapNotNull { item -> toRow(item, today) }
            .take(MAX_ROWS)
            .toList()
        return UpcomingWidgetState.Details(rows = rows, total = week.size)
    }

    /** How many rows fit into a widget [heightDp] tall: at least one, at most [MAX_ROWS]. */
    fun rowsThatFit(heightDp: Float): Int =
        ((heightDp - CHROME_HEIGHT_DP) / ROW_PITCH_DP).toInt().coerceIn(1, MAX_ROWS)

    private fun toRow(item: AgendaItem, today: LocalDate): UpcomingWidgetRow? {
        val date = AgendaSchedule.dateOf(item) ?: return null
        return UpcomingWidgetRow(
            threadId = item.threadId,
            threadName = item.threadName,
            label = item.label,
            kind = item.kind,
            date = date,
            time = item.time,
            urgent = AgendaSchedule.isUrgent(item, today),
            endTime = item.endTime
        )
    }
}
