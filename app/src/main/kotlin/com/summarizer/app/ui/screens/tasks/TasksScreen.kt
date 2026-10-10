package com.summarizer.app.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.usecase.AgendaSchedule
import com.summarizer.app.ui.components.EmptyState
import com.summarizer.app.ui.components.GroupedListDefaults
import com.summarizer.app.ui.components.agendaDate
import com.summarizer.app.ui.components.agendaTime
import com.summarizer.app.ui.components.openAddToCalendar
import com.summarizer.app.ui.components.relativeDay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Tasks tab: dates and tasks from the latest summaries of followed chats, grouped by when they
 * are due. Tasks can be ticked off (with undo), dates added to the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onThreadClick: (String) -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val screenColor = GroupedListDefaults.screenColor

    val doneMessage = stringResource(R.string.tasks_done_snackbar)
    val undoLabel = stringResource(R.string.tasks_undo)
    val noCalendarMessage = stringResource(R.string.tasks_no_calendar_app)

    val onCheckedChange: (AgendaItem, Boolean) -> Unit = { item, done ->
        viewModel.setDone(item, done)
        if (done) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = doneMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.setDone(item, false)
            }
        }
    }
    val onAddToCalendar: (AgendaItem) -> Unit = { item ->
        if (!context.openAddToCalendar(item.label, item.threadName, item.date, item.time)) {
            scope.launch { snackbarHostState.showSnackbar(noCalendarMessage) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when (val state = uiState) {
            TasksUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is TasksUiState.Ready -> if (state.groups.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = stringResource(R.string.tasks_empty_title),
                    body = stringResource(R.string.tasks_empty_body),
                    modifier = Modifier.padding(padding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 16.dp
                    )
                ) {
                    state.groups.forEach { (bucket, items) ->
                        item(key = bucket.name, contentType = "header") {
                            BucketHeader(bucket)
                        }
                        itemsIndexed(
                            items = items,
                            key = { _, item -> "${item.summaryId}-${item.kind}-${item.index}" },
                            contentType = { _, _ -> "item" }
                        ) { index, item ->
                            AgendaRow(
                                item = item,
                                today = state.today,
                                shape = GroupedListDefaults.shape(index, items.size),
                                onClick = { onThreadClick(item.threadId) },
                                onCheckedChange = { onCheckedChange(item, it) },
                                onAddToCalendar = { onAddToCalendar(item) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = GroupedListDefaults.ItemSpacing)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketHeader(bucket: AgendaSchedule.Bucket) {
    val title = stringResource(
        when (bucket) {
            AgendaSchedule.Bucket.OVERDUE -> R.string.tasks_bucket_overdue
            AgendaSchedule.Bucket.TODAY -> R.string.tasks_bucket_today
            AgendaSchedule.Bucket.TOMORROW -> R.string.tasks_bucket_tomorrow
            AgendaSchedule.Bucket.THIS_WEEK -> R.string.tasks_bucket_this_week
            AgendaSchedule.Bucket.LATER -> R.string.tasks_bucket_later
            AgendaSchedule.Bucket.NO_DATE -> R.string.tasks_bucket_no_date
        }
    )
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = if (bucket == AgendaSchedule.Bucket.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun AgendaRow(
    item: AgendaItem,
    today: LocalDate,
    shape: Shape,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onAddToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = AgendaSchedule.dateOf(item)
    val urgent = AgendaSchedule.isUrgent(item, today)
    val whenText = listOfNotNull(
        date?.let { agendaDate(it) },
        item.time?.let { agendaTime(it) },
        AgendaSchedule.daysUntil(item, today)?.takeIf { it < 0 && !item.done }?.let { relativeDay(it) }
    ).joinToString(", ")
    val supporting = listOfNotNull(item.threadName, whenText.ifEmpty { null }).joinToString(" · ")
    val isTask = item.kind == AgendaItem.Kind.TASK
    val kindLabel = if (isTask) null else stringResource(R.string.tasks_kind_date)
    val description = listOfNotNull(kindLabel, item.label, supporting).joinToString(", ")

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = shape,
        color = GroupedListDefaults.itemColor
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTask) {
                Checkbox(checked = item.done, onCheckedChange = onCheckedChange)
            } else {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clearAndSetSemantics { },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isTask) {
                IconButton(onClick = onAddToCalendar) {
                    Icon(
                        imageVector = Icons.Outlined.EditCalendar,
                        contentDescription = stringResource(R.string.tasks_add_to_calendar),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
