package com.summarizer.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Hosts [UpcomingWidget]; also refreshes it when the clock, time zone or language changes. */
class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        UpcomingWidgetEntryPoint.updater(context).onWidgetsPlaced(true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        UpcomingWidgetEntryPoint.updater(context).onWidgetsPlaced(false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                val pending = goAsync()
                UpcomingWidgetEntryPoint.updater(context).onSystemChanged()
                    .invokeOnCompletion { pending.finish() }
            }
            else -> super.onReceive(context, intent)
        }
    }
}
