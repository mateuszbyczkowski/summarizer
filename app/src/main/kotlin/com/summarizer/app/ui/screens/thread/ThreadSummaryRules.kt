package com.summarizer.app.ui.screens.thread

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.usecase.AgendaSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** What the thread screen derives from a summary and the thread's messages. */
object ThreadSummaryRules {

    /** Where a summary was written, for the engine chip. */
    enum class EngineKind { ON_DEVICE, OPENAI }

    /** Null when the summary does not say (summaries from before engines were recorded). */
    fun engineKind(engine: String?): EngineKind? = when {
        engine.isNullOrBlank() -> null
        engine.startsWith("OpenAI", ignoreCase = true) -> EngineKind.OPENAI
        else -> EngineKind.ON_DEVICE
    }

    /** Messages newer than the summary's last message; none without a summary. */
    fun newMessageCount(messages: List<Message>, latest: Summary?): Int =
        if (latest == null) 0 else messages.count { it.timestamp > latest.endTimestamp }

    fun parseDate(iso: String?): LocalDate? =
        iso?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }

    /** An event is close when it is today or within [AgendaSchedule.URGENT_DAYS] days; past events are not. */
    fun isUrgent(keyDate: KeyDate, today: LocalDate): Boolean {
        val days = parseDate(keyDate.date)?.let { ChronoUnit.DAYS.between(today, it) } ?: return false
        return days in 0..AgendaSchedule.URGENT_DAYS
    }

    /** An open task is pressing when due within [AgendaSchedule.URGENT_DAYS] days or overdue. */
    fun isUrgent(item: ActionItem, today: LocalDate): Boolean {
        if (item.done) return false
        val days = parseDate(item.due)?.let { ChronoUnit.DAYS.between(today, it) } ?: return false
        return days <= AgendaSchedule.URGENT_DAYS
    }

    /** First and last day of a set of messages, for "10–13.10"; null without messages. */
    fun dayRange(timestamps: List<Long>, zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDate, LocalDate>? {
        if (timestamps.isEmpty()) return null
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return day(timestamps.min()) to day(timestamps.max())
    }

    /**
     * A day range written compactly with numeric days: "13.10", "10–13.10", "28.09–3.10",
     * "28.12.2025–2.01.2026". Days are "d", months "MM", as in the mockups; the year only when
     * the range crosses one.
     */
    fun formatDayRange(start: LocalDate, end: LocalDate): String {
        fun dm(date: LocalDate) = "${date.dayOfMonth}.${date.monthValue.toString().padStart(2, '0')}"
        return when {
            start == end -> dm(start)
            start.year != end.year -> "${dm(start)}.${start.year}–${dm(end)}.${end.year}"
            start.month == end.month -> "${start.dayOfMonth}–${dm(end)}"
            else -> "${dm(start)}–${dm(end)}"
        }
    }
}
