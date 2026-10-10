package com.summarizer.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.summarizer.app.MainActivity
import com.summarizer.app.R
import com.summarizer.app.util.dateTimeFormats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Home-screen widget with the coming week's dates and tasks (same rules as the home screen's
 * "This week" card). With the app lock on it shows only how many things are coming up.
 */
class UpcomingWidget : GlanceAppWidget() {

    // Exact sizes: the number of rows follows the widget's height
    override val sizeMode: SizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val updater = UpcomingWidgetEntryPoint.updater(context)
        val loadedAt = updater.refreshes.value
        val initial = updater.loadState()
        provideContent {
            // Reload when a refresh is requested while this session is still running
            val state by remember {
                updater.refreshes.filter { it != loadedAt }.mapLatest { updater.loadState() }
            }.collectAsState(initial)
            GlanceTheme {
                UpcomingContent(state)
            }
        }
    }
}

// Rows are 48dp touch targets, the header 40dp; heights match UpcomingWidgetModel
private val PADDING_HORIZONTAL = 12.dp
private val PADDING_VERTICAL = 8.dp
private val HEADER_HEIGHT = 40.dp
private val ROW_HEIGHT = 48.dp
private val BADGE_SIZE = 36.dp

@Composable
private fun UpcomingContent(state: UpcomingWidgetState) {
    val context = LocalContext.current
    val openApp = actionStartActivity(openAppIntent(context))
    val maxRows = UpcomingWidgetModel.rowsThatFit(LocalSize.current.height.value)
    val shownRows = (state as? UpcomingWidgetState.Details)?.rows?.take(maxRows).orEmpty()
    val more = (state as? UpcomingWidgetState.Details)?.let { it.total - shownRows.size } ?: 0

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(horizontal = PADDING_HORIZONTAL, vertical = PADDING_VERTICAL)
    ) {
        Header(more = more, onClick = openApp)
        when (state) {
            is UpcomingWidgetState.CountOnly -> Message(
                title = if (state.count == 0) {
                    context.getString(R.string.widget_upcoming_empty)
                } else {
                    context.resources.getQuantityString(R.plurals.widget_upcoming_count, state.count, state.count)
                },
                subtitle = context.getString(R.string.widget_upcoming_hidden),
                onClick = openApp
            )
            UpcomingWidgetState.Unavailable -> Message(
                title = context.getString(R.string.widget_upcoming_unavailable),
                subtitle = null,
                onClick = openApp
            )
            is UpcomingWidgetState.Details ->
                if (shownRows.isEmpty()) {
                    Message(
                        title = context.getString(R.string.widget_upcoming_empty),
                        subtitle = null,
                        onClick = openApp
                    )
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        shownRows.forEach { row -> AgendaRow(row) }
                    }
                }
        }
    }
}

@Composable
private fun Header(more: Int, onClick: Action) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp)
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = context.getString(R.string.widget_upcoming_title),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        if (more > 0) {
            Text(
                text = context.getString(R.string.widget_upcoming_more, more),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AgendaRow(row: UpcomingWidgetRow) {
    val context = LocalContext.current
    val locale = context.locale()
    val formats = dateTimeFormats(context, locale)
    val weekday = formats.weekdayBadge(row.date)
    val time = row.time?.let(formats::time)
    val secondary = listOfNotNull(time, row.threadName).joinToString(" · ")
    val spokenDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(row.date)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .cornerRadius(android.R.dimen.system_app_widget_inner_radius)
            .clickable(actionStartActivity(openSummaryIntent(context, row.threadId)))
            .semantics {
                contentDescription = listOfNotNull(spokenDate, time, row.label, row.threadName).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateBadge(day = row.date.dayOfMonth.toString(), weekday = weekday, urgent = row.urgent)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.label,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                maxLines = 1
            )
            Text(
                text = secondary,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DateBadge(day: String, weekday: String, urgent: Boolean) {
    val container = if (urgent) GlanceTheme.colors.errorContainer else GlanceTheme.colors.secondaryContainer
    val content = if (urgent) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier = GlanceModifier
            .size(BADGE_SIZE)
            .background(container)
            .cornerRadius(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day,
                style = TextStyle(color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = weekday,
                style = TextStyle(color = content, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Message(title: String, subtitle: String?, onClick: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 2
        )
        if (subtitle != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}

private fun Context.locale(): Locale = resources.configuration.locales[0]

/** Opens the app where the user left it (behind the app lock, if on). */
private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/** Opens the summary of a thread (behind the app lock, if on). */
private fun openSummaryIntent(context: Context, threadId: String): Intent =
    openAppIntent(context).putExtra(MainActivity.EXTRA_SUMMARY_THREAD_ID, threadId)
