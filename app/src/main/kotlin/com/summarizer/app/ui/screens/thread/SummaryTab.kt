package com.summarizer.app.ui.screens.thread

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.summarizer.app.BuildConfig
import com.summarizer.app.R
import com.summarizer.app.domain.ai.SummaryDates
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import com.summarizer.app.ui.components.EmptyState
import com.summarizer.app.ui.components.HighlightedText
import com.summarizer.app.ui.components.agendaDate
import com.summarizer.app.ui.components.agendaTime
import com.summarizer.app.ui.components.rememberReducedMotion
import com.summarizer.app.ui.components.relativeDay
import com.summarizer.app.ui.components.weekdayShort
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Gutter = 16.dp
private val SectionShape = RoundedCornerShape(20.dp)

/** Space below the list so the FAB never covers the last section. */
private val FabClearance = 88.dp

/**
 * The summary tab: generation progress or error, the history and engine chips, then the summary
 * itself (overview, key dates, tasks, announcements, topics) and a footer with feedback.
 */
@Composable
internal fun SummaryTab(
    state: ThreadUiState.Ready,
    listState: LazyListState,
    bottomPadding: Dp,
    revealSummaryId: Long?,
    actions: ThreadActions,
    callbacks: SummaryCallbacks,
    onRetry: () -> Unit
) {
    val generation = state.generation
    val summary = state.shown

    if (summary == null && generation == null) {
        EmptyState(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.thread_no_summary_title),
            body = stringResource(R.string.thread_no_summary_body),
            // The FAB is the one action to summarize
            modifier = Modifier.padding(bottom = bottomPadding)
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding + FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (generation) {
            is SummaryGenerationCoordinator.State.Running -> {
                item(key = "generation") {
                    GenerationCard(
                        running = generation,
                        onCancel = actions.onCancel,
                        modifier = Modifier.padding(horizontal = Gutter)
                    )
                }
                if (summary != null) {
                    item(key = "previous") { PreviousSummaryLabel(summary, state.today) }
                    summarySections(summary, state.today, interactive = false, reveal = false, callbacks = callbacks)
                }
                return@LazyColumn
            }
            is SummaryGenerationCoordinator.State.Failed -> item(key = "failed") {
                FailedCard(
                    failed = generation,
                    onRetry = onRetry,
                    onDismiss = actions.onDismissError,
                    modifier = Modifier.padding(horizontal = Gutter)
                )
            }
            null -> Unit
        }
        if (summary == null) return@LazyColumn

        item(key = "chips") {
            SummaryChips(
                summaries = state.summaries,
                shown = summary,
                today = state.today,
                onSelect = actions.onSelectSummary
            )
        }
        summarySections(summary, state.today, interactive = true, reveal = summary.id == revealSummaryId, callbacks = callbacks)
        item(key = "footer") {
            SummaryFooter(
                summary = summary,
                onRate = { actions.onRate(summary, it) },
                onCopy = { callbacks.onCopy(summary) },
                onShare = { callbacks.onShare(summary) }
            )
        }
        val raw = summary.rawAIResponse
        if (BuildConfig.DEBUG && !raw.isNullOrBlank()) {
            item(key = "raw") { RawOutput(raw) }
        }
    }
}

/**
 * The sections of a summary. Previous summaries shown under a running generation are dimmed and
 * read-only ([interactive] false); a freshly generated one fades in section by section ([reveal]).
 */
private fun LazyListScope.summarySections(
    summary: Summary,
    today: LocalDate,
    interactive: Boolean,
    reveal: Boolean,
    callbacks: SummaryCallbacks
) {
    var order = 0

    if (summary.overview.isNotBlank()) {
        val index = order++
        item(key = "overview") {
            OverviewCard(summary, modifier = Modifier.padding(horizontal = Gutter).sectionStyle(index, interactive, reveal))
        }
    }
    if (summary.keyDates.isNotEmpty()) {
        val index = order++
        item(key = "dates") {
            Column(
                modifier = Modifier.sectionStyle(index, interactive, reveal),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.thread_key_dates),
                    modifier = Modifier.padding(horizontal = Gutter)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(summary.keyDates) { _, keyDate ->
                        KeyDateTile(
                            keyDate = keyDate,
                            today = today,
                            onAddToCalendar = if (interactive) {
                                { callbacks.onAddToCalendar(summary, keyDate) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
    if (summary.actionItems.isNotEmpty()) {
        val index = order++
        item(key = "tasks") {
            TasksSection(
                summary = summary,
                today = today,
                onCheckedChange = if (interactive) {
                    { taskIndex, done -> callbacks.onTaskChecked(summary, taskIndex, done) }
                } else {
                    null
                },
                modifier = Modifier.padding(horizontal = Gutter).sectionStyle(index, interactive, reveal)
            )
        }
    }
    if (summary.announcements.isNotEmpty()) {
        val index = order++
        item(key = "announcements") {
            AnnouncementsSection(summary.announcements, modifier = Modifier.padding(horizontal = Gutter).sectionStyle(index, interactive, reveal))
        }
    }
    if (summary.keyTopics.isNotEmpty()) {
        val index = order++
        item(key = "topics") {
            TopicsSection(summary.keyTopics, modifier = Modifier.padding(horizontal = Gutter).sectionStyle(index, interactive, reveal))
        }
    }
}

/**
 * Dims a read-only section (a previous summary under a running generation) and fades a freshly
 * generated one in after the sections above it, unless the user turned animations off.
 */
@Composable
private fun Modifier.sectionStyle(index: Int, interactive: Boolean, reveal: Boolean): Modifier {
    val base = if (interactive) this else this.alpha(0.5f)
    return base.revealOrder(index, reveal)
}

@Composable
private fun Modifier.revealOrder(index: Int, active: Boolean): Modifier {
    if (!active || rememberReducedMotion()) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 90L)
        progress.animateTo(1f, tween(durationMillis = 350))
    }
    val shift = with(LocalDensity.current) { 12.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * shift
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Today, 17:02 ▾" (the summary history) and where the summary was written. */
@Composable
private fun SummaryChips(
    summaries: List<Summary>,
    shown: Summary,
    today: LocalDate,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HistoryChip(summaries, shown, today, onSelect)
        EngineLabel(shown.engine)
    }
}

@Composable
private fun HistoryChip(
    summaries: List<Summary>,
    shown: Summary,
    today: LocalDate,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = summaryTime(shown.generatedAt, today)
    val hasHistory = summaries.size > 1
    val description = stringResource(R.string.thread_history_cd, label)
    Box {
        Surface(
            onClick = { expanded = true },
            enabled = hasHistory,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp)
                .then(if (hasHistory) Modifier.semantics { contentDescription = description } else Modifier)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = if (hasHistory) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge)
                if (hasHistory) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            summaries.forEachIndexed { index, summary ->
                val range = dayRange(localDate(summary.startTimestamp), localDate(summary.endTimestamp))
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = summaryTime(summary.generatedAt, today) +
                                    if (index == 0) " · " + stringResource(R.string.thread_history_latest) else "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(
                                    R.string.thread_subtitle,
                                    pluralStringResource(R.plurals.thread_message_count, summary.messageCount, summary.messageCount),
                                    range
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (summary.id == shown.id) Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(summary.id)
                    },
                    modifier = Modifier.semantics { selected = summary.id == shown.id }
                )
            }
        }
    }
}

@Composable
private fun EngineLabel(engine: String?) {
    val kind = ThreadSummaryRules.engineKind(engine) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (kind == ThreadSummaryRules.EngineKind.OPENAI) Icons.Outlined.Cloud else Icons.Outlined.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(
                if (kind == ThreadSummaryRules.EngineKind.OPENAI) R.string.thread_engine_openai else R.string.thread_engine_on_device
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "In short": the overview with its dates and times marked. */
@Composable
private fun OverviewCard(summary: Summary, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val marks = remember(summary.overview, summary.endTimestamp) {
        SummaryDates.findDateTimeSpans(summary.overview, localDate(summary.endTimestamp))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(colors.primaryContainer, lerp(colors.primaryContainer, colors.tertiaryContainer, 0.6f))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.thread_in_short),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
                modifier = Modifier.semantics { heading() }
            )
        }
        HighlightedText(
            text = summary.overview,
            marks = marks,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onPrimaryContainer
        )
    }
}

/** Day and weekday, what happens, the time and details; tapping adds it to the calendar. */
@Composable
private fun KeyDateTile(keyDate: KeyDate, today: LocalDate, onAddToCalendar: (() -> Unit)?) {
    val date = ThreadSummaryRules.parseDate(keyDate.date)
    val urgent = ThreadSummaryRules.isUrgent(keyDate, today)
    val colors = MaterialTheme.colorScheme
    val relative = date?.let { ChronoUnit.DAYS.between(today, it) }?.takeIf { urgent }?.let { relativeDay(it) }
    val details = listOfNotNull(keyDate.time?.let { agendaTime(it) }, keyDate.detail, relative).joinToString(" · ")
    val addLabel = stringResource(R.string.tasks_add_to_calendar)
    val description = listOfNotNull(
        keyDate.label,
        date?.let { agendaDate(it) } ?: stringResource(R.string.thread_date_unknown),
        details.ifEmpty { null },
        if (onAddToCalendar != null) addLabel else null
    ).joinToString(", ")

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.clearAndSetSemantics { }) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (date != null) {
                        Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = weekdayShort(date),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .alpha(0.75f)
                                .padding(bottom = 4.dp)
                        )
                    } else {
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Text(
                    text = keyDate.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = details.ifEmpty { if (date == null) stringResource(R.string.thread_date_unknown) else "" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.75f)
                )
            }
            if (onAddToCalendar != null) {
                Icon(
                    imageVector = Icons.Outlined.EditCalendar,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(18.dp)
                        .alpha(0.7f)
                )
            }
        }
    }
    val tileModifier = Modifier
        .width(140.dp)
        .defaultMinSize(minHeight = 112.dp)
        .semantics { contentDescription = description }
    val shape = RoundedCornerShape(16.dp)
    val container = if (urgent) colors.errorContainer else colors.surfaceContainerHigh
    val onContainer = if (urgent) colors.onErrorContainer else colors.onSurface
    if (onAddToCalendar != null) {
        Surface(onClick = onAddToCalendar, modifier = tileModifier, shape = shape, color = container, contentColor = onContainer) {
            content()
        }
    } else {
        Surface(modifier = tileModifier, shape = shape, color = container, contentColor = onContainer) { content() }
    }
}

@Composable
private fun TasksSection(
    summary: Summary,
    today: LocalDate,
    onCheckedChange: ((Int, Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val done = summary.actionItems.count { it.done }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            icon = Icons.Outlined.TaskAlt,
            title = stringResource(R.string.thread_todo),
            trailing = pluralStringResource(R.plurals.thread_todo_progress, summary.actionItems.size, done, summary.actionItems.size)
        )
        Surface(shape = SectionShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                summary.actionItems.forEachIndexed { index, item ->
                    TaskRow(
                        item = item,
                        today = today,
                        onCheckedChange = onCheckedChange?.let { change -> { checked: Boolean -> change(index, checked) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(item: ActionItem, today: LocalDate, onCheckedChange: ((Boolean) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val urgent = ThreadSummaryRules.isUrgent(item, today)
    val due = ThreadSummaryRules.parseDate(item.due)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = item.done,
                enabled = onCheckedChange != null,
                role = Role.Checkbox,
                onValueChange = { onCheckedChange?.invoke(it) }
            )
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = item.done, onCheckedChange = null, enabled = onCheckedChange != null, modifier = Modifier.size(48.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 13.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.task,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) colors.onSurfaceVariant else colors.onSurface
            )
            item.assignedTo?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (due != null && !item.done) {
                Spacer(modifier = Modifier.height(4.dp))
                DueChip(text = stringResource(R.string.thread_task_due, dueDay(due)), urgent = urgent)
            }
        }
    }
}

@Composable
private fun DueChip(text: String, urgent: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (urgent) colors.errorContainer else colors.surfaceContainerLow,
        contentColor = if (urgent) colors.onErrorContainer else colors.onSurfaceVariant,
        border = if (urgent) null else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (urgent) Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AnnouncementsSection(announcements: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(icon = Icons.Outlined.Campaign, title = stringResource(R.string.thread_announcements))
        Surface(shape = SectionShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                announcements.forEach { text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicsSection(topics: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(icon = Icons.AutoMirrored.Outlined.Sort, title = stringResource(R.string.thread_topics))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topics.forEach { topic ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** Messages covered, the engine, copy and share, and thumbs up / down. */
@Composable
private fun SummaryFooter(
    summary: Summary,
    onRate: (Int) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val range = dayRange(localDate(summary.startTimestamp), localDate(summary.endTimestamp))
    val meta = listOfNotNull(
        pluralStringResource(R.plurals.thread_message_count, summary.messageCount, summary.messageCount),
        range,
        summary.engine?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    Column(modifier = Modifier.padding(horizontal = Gutter)) {
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.thread_copy))
            }
            TextButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.thread_share_short))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconToggleButton(checked = summary.rating == 1, onCheckedChange = { onRate(1) }) {
                Icon(
                    imageVector = if (summary.rating == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.thread_rate_up),
                    tint = if (summary.rating == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconToggleButton(checked = summary.rating == -1, onCheckedChange = { onRate(-1) }) {
                Icon(
                    imageVector = if (summary.rating == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(R.string.thread_rate_down),
                    tint = if (summary.rating == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Debug builds only: what the model wrote, collapsed. */
@Composable
private fun RawOutput(raw: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = Gutter)) {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.thread_raw_output))
        }
        if (expanded) {
            Surface(shape = SectionShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    text = raw,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviousSummaryLabel(summary: Summary, today: LocalDate) {
    Row(
        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(R.string.thread_previous_summary, summaryTime(summary.generatedAt, today)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FailedCard(
    failed: SummaryGenerationCoordinator.State.Failed,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container = if (failed.nothingNew) colors.surfaceContainerHigh else colors.errorContainer
    val content = if (failed.nothingNew) colors.onSurface else colors.onErrorContainer
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = content
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(end = 8.dp)) {
                Icon(if (failed.nothingNew) Icons.Outlined.Info else Icons.Outlined.ErrorOutline, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(if (failed.nothingNew) R.string.thread_nothing_new_title else R.string.thread_failed_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!failed.nothingNew) {
                        Text(text = stringResource(R.string.thread_failed_body), style = MaterialTheme.typography.bodyMedium)
                    }
                    // The error itself is technical English, useful only while developing
                    if (BuildConfig.DEBUG && failed.message.isNotBlank()) {
                        Text(text = failed.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss), color = content) }
                if (!failed.nothingNew) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = content) }
                }
            }
        }
    }
}
