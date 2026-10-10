package com.summarizer.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

@Composable
@ReadOnlyComposable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** How long ago [timestamp] was, short: "just now", "5 min ago", "1 h ago", "3 d ago", then the date. */
@Composable
@ReadOnlyComposable
fun relativeTimeAgo(timestamp: Long, now: Long): String {
    val diff = (now - timestamp).coerceAtLeast(0)
    return when {
        diff < MINUTE -> stringResource(R.string.rel_time_just_now)
        diff < HOUR -> (diff / MINUTE).toInt().let { pluralStringResource(R.plurals.rel_time_minutes_ago, it, it) }
        diff < DAY -> (diff / HOUR).toInt().let { pluralStringResource(R.plurals.rel_time_hours_ago, it, it) }
        diff < 7 * DAY -> (diff / DAY).toInt().let { pluralStringResource(R.plurals.rel_time_days_ago, it, it) }
        else -> shortDate(toLocalDate(timestamp))
    }
}

/** When the last message arrived, chat-list style: "17:42" today, "Yesterday", "Mon" this week, then "12 Oct". */
@Composable
@ReadOnlyComposable
fun chatListTime(timestamp: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(timestamp)
    val days = ChronoUnit.DAYS.between(instant.atZone(zone).toLocalDate(), toLocalDate(now))
    return when {
        days <= 0 -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale()).format(instant.atZone(zone))
        days == 1L -> stringResource(R.string.rel_time_yesterday)
        days < 7 -> DateTimeFormatter.ofPattern(stringResource(R.string.pattern_weekday), locale()).format(instant.atZone(zone))
        else -> shortDate(instant.atZone(zone).toLocalDate())
    }
}

@Composable
@ReadOnlyComposable
private fun shortDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.pattern_short_date), locale()).format(date)

/** A day of the agenda: "Fri, 17 Oct". */
@Composable
@ReadOnlyComposable
fun agendaDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.pattern_agenda_date), locale()).format(date)

/** Short weekday of a date, upper case, for date badges: "FRI". */
@Composable
@ReadOnlyComposable
fun weekdayShort(date: LocalDate): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.pattern_weekday), locale()).format(date)
        .trimEnd('.')
        .uppercase(locale())

/** An HH:mm time in the phone's format ("7:30 AM" or "07:30"); the text itself if it does not parse. */
@Composable
@ReadOnlyComposable
fun agendaTime(time: String): String {
    val parsed = try {
        LocalTime.parse(time)
    } catch (e: DateTimeParseException) {
        null
    }
    return parsed?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale()).format(it) } ?: time
}

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
