package com.summarizer.app.util

import android.content.Context
import android.text.format.DateFormat
import java.util.Locale

/** [DateTimeFormats] for the app's current locale and the phone's 12/24-hour setting. */
fun dateTimeFormats(context: Context, locale: Locale = context.resources.configuration.locales[0]): DateTimeFormats =
    DateTimeFormats(locale, DateFormat.is24HourFormat(context)) { skeleton ->
        DateFormat.getBestDateTimePattern(locale, skeleton)
    }
