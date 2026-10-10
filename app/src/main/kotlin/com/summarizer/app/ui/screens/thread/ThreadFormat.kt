package com.summarizer.app.ui.screens.thread

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.ui.components.rememberDateTimeFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun localDate(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

/** Time of day in the phone's format: "17:02" or "5:02 PM". */
@Composable
fun timeOfDay(timestamp: Long): String =
    rememberDateTimeFormats().time(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime())

/** When a summary was written: "Today, 17:02", "Yesterday, 17:02", "Mon, Oct 12, 17:02". */
@Composable
fun summaryTime(timestamp: Long, today: LocalDate): String {
    val day = localDate(timestamp)
    val time = timeOfDay(timestamp)
    return when (day) {
        today -> stringResource(R.string.thread_history_today, time)
        today.minusDays(1) -> stringResource(R.string.thread_history_yesterday, time)
        else -> stringResource(R.string.thread_history_date, rememberDateTimeFormats().weekdayDate(day), time)
    }
}

/** Day header of the messages tab: "Today", "Yesterday", "Mon, Oct 12". */
@Composable
fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.thread_day_today)
    today.minusDays(1) -> stringResource(R.string.thread_day_yesterday)
    else -> rememberDateTimeFormats().weekdayDate(date)
}

/** A due date for task chips: "Wed, Oct 15" / "śr., 15 paź". */
@Composable
fun dueDay(date: LocalDate): String = rememberDateTimeFormats().weekdayDate(date)

/** First and last day of a summary or chat, compact: "Oct 9–13" / "9–13 paź". */
@Composable
fun dayRange(start: LocalDate, end: LocalDate): String = rememberDateTimeFormats().dayRange(start, end)
