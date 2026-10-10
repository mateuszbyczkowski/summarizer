package com.summarizer.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private const val DEFAULT_EVENT_MILLIS = 60 * 60 * 1000L

/**
 * Opens the calendar app's "new event" screen filled with [title], [description] and, when
 * known, the day ([isoDate], yyyy-MM-dd) and time ([time], HH:mm); without a time the event is
 * all-day. The user saves it there, so no calendar permission is needed.
 *
 * @return false when no calendar app can take the event
 */
fun Context.openAddToCalendar(title: String, description: String?, isoDate: String?, time: String?): Boolean {
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
    description?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }

    val date = isoDate?.let { runCatchingParse { LocalDate.parse(it) } }
    if (date != null) {
        val zone = ZoneId.systemDefault()
        val startTime = time?.let { runCatchingParse { LocalTime.parse(it) } }
        if (startTime != null) {
            val begin = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + DEFAULT_EVENT_MILLIS)
        } else {
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.atStartOfDay(zone).toInstant().toEpochMilli())
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }
    }

    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private inline fun <T> runCatchingParse(parse: () -> T): T? =
    try {
        parse()
    } catch (e: DateTimeParseException) {
        null
    }
