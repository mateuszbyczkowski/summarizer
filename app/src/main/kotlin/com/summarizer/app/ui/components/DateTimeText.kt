package com.summarizer.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.util.DateTimeFormats
import com.summarizer.app.util.dateTimeFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/**
 * Date and time formats for the app's current locale (recomposes when it changes) and the phone's
 * 12/24-hour setting.
 */
@Composable
fun rememberDateTimeFormats(): DateTimeFormats {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) { dateTimeFormats(context, locale) }
}

/** How long ago [timestamp] was, short: "just now", "5 min ago", "1 h ago", "3 d ago", then the date. */
@Composable
fun relativeTimeAgo(timestamp: Long, now: Long): String {
    val diff = (now - timestamp).coerceAtLeast(0)
    return when {
        diff < MINUTE -> stringResource(R.string.rel_time_just_now)
        diff < HOUR -> (diff / MINUTE).toInt().let { pluralStringResource(R.plurals.rel_time_minutes_ago, it, it) }
        diff < DAY -> (diff / HOUR).toInt().let { pluralStringResource(R.plurals.rel_time_hours_ago, it, it) }
        diff < 7 * DAY -> (diff / DAY).toInt().let { pluralStringResource(R.plurals.rel_time_days_ago, it, it) }
        else -> rememberDateTimeFormats().pastDate(toLocalDate(timestamp), toLocalDate(now))
    }
}

/** When the last message arrived, chat-list style: "17:42" today, "Yesterday", "Mon" this week, then "Oct 12". */
@Composable
fun chatListTime(timestamp: Long, now: Long): String {
    val formats = rememberDateTimeFormats()
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val days = ChronoUnit.DAYS.between(dateTime.toLocalDate(), toLocalDate(now))
    return when {
        days <= 0 -> formats.time(dateTime.toLocalTime())
        days == 1L -> stringResource(R.string.rel_time_yesterday)
        days < 7 -> formats.weekday(dateTime.toLocalDate())
        else -> formats.pastDate(dateTime.toLocalDate(), toLocalDate(now))
    }
}

/** A day of the agenda: "Fri, Oct 17" / "pt., 17 paź". */
@Composable
fun agendaDate(date: LocalDate): String = rememberDateTimeFormats().weekdayDate(date)

/** Short weekday of a date, upper case, for date badges: "FRI". */
@Composable
fun weekdayShort(date: LocalDate): String = rememberDateTimeFormats().weekdayBadge(date)

/**
 * An HH:mm time in the phone's format ("7:30 AM" or "07:30"), with the end of its range when
 * there is one ("19:30–21:00"); the text itself if it does not parse.
 */
@Composable
fun agendaTime(time: String, endTime: String? = null): String = rememberDateTimeFormats().timeRange(time, endTime)

/** Distance of a day from today: "today", "tomorrow", "in 3 days", "2 days overdue". */
@Composable
@ReadOnlyComposable
fun relativeDay(days: Long): String = when {
    days == 0L -> stringResource(R.string.agenda_today)
    days == 1L -> stringResource(R.string.agenda_tomorrow)
    days > 1 -> days.toInt().let { pluralStringResource(R.plurals.agenda_in_days, it, it) }
    else -> (-days).toInt().let { pluralStringResource(R.plurals.agenda_overdue_days, it, it) }
}

private fun toLocalDate(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
