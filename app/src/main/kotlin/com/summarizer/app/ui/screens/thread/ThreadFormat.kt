package com.summarizer.app.ui.screens.thread

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.ui.components.agendaDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

internal fun localDate(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

/** Time of day in the phone's format: "17:02" or "5:02 PM". */
@Composable
@ReadOnlyComposable
fun timeOfDay(timestamp: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

/** When a summary was written: "Today, 17:02", "Yesterday, 17:02", "Mon, 12 Oct, 17:02". */
@Composable
@ReadOnlyComposable
fun summaryTime(timestamp: Long, today: LocalDate): String {
    val day = localDate(timestamp)
    val time = timeOfDay(timestamp)
    return when (day) {
        today -> stringResource(R.string.thread_history_today, time)
        today.minusDays(1) -> stringResource(R.string.thread_history_yesterday, time)
        else -> stringResource(R.string.thread_history_date, agendaDate(day), time)
    }
}

/** Day header of the messages tab: "Today", "Yesterday", "Mon, 12 Oct". */
@Composable
@ReadOnlyComposable
fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.thread_day_today)
    today.minusDays(1) -> stringResource(R.string.thread_day_yesterday)
    else -> agendaDate(date)
}

/** A due date for task chips, as in copied summaries: "Wed 15.10". */
@Composable
@ReadOnlyComposable
fun dueDay(date: LocalDate): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.summary_text_date_pattern), locale()).format(date)
