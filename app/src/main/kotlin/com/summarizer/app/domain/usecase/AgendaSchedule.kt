package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.model.AgendaItem
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Which agenda items matter when, relative to [today]: the task list's date groups, the home
 * screen's "this week" card and the task badge. Works on [ObserveAgendaUseCase]'s sorted list
 * and keeps its order.
 */
object AgendaSchedule {

    /** Days counted as "this week", today included. */
    const val WEEK_DAYS = 7

    /** Items due within this many days (or overdue) are shown as urgent. */
    const val URGENT_DAYS = 2

    /** Groups of the task list, in display order. */
    enum class Bucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER, NO_DATE }

    fun dateOf(item: AgendaItem): LocalDate? =
        item.date?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }

    /** Days from [today] to the item's date (negative when past), or null without a date. */
    fun daysUntil(item: AgendaItem, today: LocalDate): Long? =
        dateOf(item)?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * The task list group of an item, or null when it no longer matters: dates that have passed
     * and finished tasks whose due date has passed. Open overdue tasks stay, as [Bucket.OVERDUE].
     */
    fun bucketOf(item: AgendaItem, today: LocalDate): Bucket? {
        val days = daysUntil(item, today) ?: return Bucket.NO_DATE
        return when {
            days < 0 -> if (item.isOpenTask) Bucket.OVERDUE else null
            days == 0L -> Bucket.TODAY
            days == 1L -> Bucket.TOMORROW
            days < WEEK_DAYS -> Bucket.THIS_WEEK
            else -> Bucket.LATER
        }
    }

    /** The task list: non-empty groups in display order, items in agenda order. */
    fun group(items: List<AgendaItem>, today: LocalDate): List<Pair<Bucket, List<AgendaItem>>> =
        items
            .mapNotNull { item -> bucketOf(item, today)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .toList()

    /**
     * Whether an item belongs to the coming week: dates from today on, and open tasks due by
     * then, including tasks that became overdue during the past week.
     */
    fun isThisWeek(item: AgendaItem, today: LocalDate): Boolean {
        val days = daysUntil(item, today) ?: return false
        return when (item.kind) {
            AgendaItem.Kind.DATE -> days in 0 until WEEK_DAYS
            AgendaItem.Kind.TASK -> item.isOpenTask && days in -WEEK_DAYS until WEEK_DAYS
        }
    }

    /** The first [limit] items of the coming week, for the home screen card. */
    fun thisWeek(items: List<AgendaItem>, today: LocalDate, limit: Int = 3): List<AgendaItem> =
        items.asSequence().filter { isThisWeek(it, today) }.take(limit).toList()

    /** Open tasks due within the coming week (or overdue during the past week), for the badge. */
    fun dueSoonTaskCount(items: List<AgendaItem>, today: LocalDate): Int =
        items.count { it.kind == AgendaItem.Kind.TASK && isThisWeek(it, today) }

    /** Due within [URGENT_DAYS] days or overdue, and not done. */
    fun isUrgent(item: AgendaItem, today: LocalDate): Boolean {
        if (item.kind == AgendaItem.Kind.TASK && item.done) return false
        val days = daysUntil(item, today) ?: return false
        return days <= URGENT_DAYS
    }

    private val AgendaItem.isOpenTask get() = kind == AgendaItem.Kind.TASK && !done
}
