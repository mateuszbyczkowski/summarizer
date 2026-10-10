package com.summarizer.app.util

import android.content.Context
import com.summarizer.app.R
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Summary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * A summary as plain text for copying and sharing (e.g. into WhatsApp): overview, dates,
 * tasks, announcements and topics, with dates written for people ("pt. 17.10").
 */
object SummaryFormatter {

    /** Section headings and patterns, from resources so they follow the app language. */
    data class Labels(
        val dates: String,
        val tasks: String,
        val announcements: String,
        val topics: String,
        /** Format with one argument, the date: "by %1$s" */
        val due: String,
        /** [DateTimeFormatter] pattern for a day, e.g. "EEE d.MM" */
        val datePattern: String
    ) {
        companion object {
            fun from(context: Context) = Labels(
                dates = context.getString(R.string.summary_text_dates),
                tasks = context.getString(R.string.summary_text_tasks),
                announcements = context.getString(R.string.summary_text_announcements),
                topics = context.getString(R.string.summary_text_topics),
                due = context.getString(R.string.summary_text_due),
                datePattern = context.getString(R.string.summary_text_date_pattern)
            )
        }
    }

    fun toPlainText(context: Context, summary: Summary): String =
        toPlainText(summary, Labels.from(context), context.resources.configuration.locales[0])

    fun toPlainText(summary: Summary, labels: Labels, locale: Locale = Locale.getDefault()): String {
        val dateFormat = DateTimeFormatter.ofPattern(labels.datePattern, locale)
        fun day(iso: String?): String? = iso?.let {
            try {
                LocalDate.parse(it).format(dateFormat)
            } catch (e: DateTimeParseException) {
                it
            }
        }

        return buildString {
            appendLine(summary.threadName)
            if (summary.overview.isNotBlank()) appendLine(summary.overview)

            section(labels.dates, summary.keyDates) { "• ${keyDateLine(it, day(it.date))}" }
            section(labels.tasks, summary.actionItems) { item ->
                val due = day(item.due)?.let { " (${labels.due.format(it)})" }.orEmpty()
                "${if (item.done) "☑" else "☐"} ${item.task}$due"
            }
            section(labels.announcements, summary.announcements) { "• $it" }
            if (summary.keyTopics.isNotEmpty()) {
                appendLine()
                appendLine("${labels.topics}: ${summary.keyTopics.joinToString(" · ")}")
            }
        }.trimEnd()
    }

    private fun keyDateLine(keyDate: KeyDate, day: String?): String {
        val whenText = listOfNotNull(day, keyDate.time).joinToString(", ")
        val what = keyDate.label + (keyDate.detail?.let { " ($it)" } ?: "")
        return if (whenText.isEmpty()) what else "$whenText – $what"
    }

    private fun <T> StringBuilder.section(title: String, items: List<T>, line: (T) -> String) {
        if (items.isEmpty()) return
        appendLine()
        appendLine("$title:")
        items.forEach { appendLine(line(it)) }
    }
}
