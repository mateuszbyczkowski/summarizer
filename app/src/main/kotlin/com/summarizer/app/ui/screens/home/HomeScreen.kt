package com.summarizer.app.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.model.ModelSetupState
import com.summarizer.app.domain.usecase.AgendaSchedule
import com.summarizer.app.ui.components.AgendaDateBadge
import com.summarizer.app.ui.components.EmptyState
import com.summarizer.app.ui.components.GroupedListDefaults
import com.summarizer.app.ui.components.PermissionCard
import com.summarizer.app.ui.components.SearchEntryBar
import com.summarizer.app.ui.components.agendaDate
import com.summarizer.app.ui.components.agendaTime
import com.summarizer.app.ui.components.rememberNotificationPermissionRequest
import com.summarizer.app.ui.components.relativeDay
import com.summarizer.app.util.PermissionHelper
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val KEY_PERMISSION = "permission"
private const val KEY_MODEL_SETUP = "model_setup"
private const val KEY_THIS_WEEK = "this_week"
private const val KEY_FOLLOWED_HEADER = "followed_header"
private const val KEY_FOLLOWED_EMPTY = "followed_empty"
private const val KEY_OTHERS_TOGGLE = "others_toggle"

/**
 * Home: search entry, the coming week's dates and tasks, followed chats, and the other chats
 * folded away below them.
 */
@Composable
fun HomeScreen(
    onThreadClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onAllTasksClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val autoSummaryHour by viewModel.autoSummaryHour.collectAsStateWithLifecycle()
    val modelSetup by viewModel.modelSetup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context)) }

    // Notifications (smart alerts, summary ready) need runtime permission on Android 13+
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    LaunchedEffect(Unit) { requestNotificationPermission() }

    // The user may grant access in Settings or change the schedule while away
    LifecycleResumeEffect(Unit) {
        hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
        viewModel.refreshAutoSummary()
        onPauseOrDispose { }
    }

    HomeContent(
        uiState = uiState,
        hasPermission = hasPermission,
        autoSummaryHour = autoSummaryHour,
        modelSetup = modelSetup,
        onRetryModelSetup = viewModel::retryModelSetup,
        onCancelModelSetup = viewModel::cancelModelSetup,
        onSearchClick = onSearchClick,
        onAllTasksClick = onAllTasksClick,
        onThreadClick = onThreadClick,
        onSetFollowed = viewModel::setFollowed,
        onSummarize = viewModel::summarize
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    hasPermission: Boolean,
    autoSummaryHour: Int?,
    modelSetup: ModelSetupState?,
    onRetryModelSetup: () -> Unit,
    onCancelModelSetup: () -> Unit,
    onSearchClick: () -> Unit,
    onAllTasksClick: () -> Unit,
    onThreadClick: (String) -> Unit,
    onSetFollowed: (String, Boolean) -> Unit,
    onSummarize: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var othersExpanded by rememberSaveable { mutableStateOf(false) }
    val screenColor = GroupedListDefaults.screenColor
    // Like a top app bar: the area behind the search bar tints once content scrolls under it
    val scrolled = listState.canScrollBackward
    val topColor by animateColorAsState(
        targetValue = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else screenColor,
        label = "searchAreaColor"
    )

    Scaffold(
        containerColor = screenColor,
        topBar = {
            HomeSearchArea(color = topColor, onSearchClick = onSearchClick)
        }
    ) { padding ->
        when (uiState) {
            HomeUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is HomeUiState.Ready -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp
                )
            ) {
                if (!hasPermission) {
                    item(key = KEY_PERMISSION) {
                        PermissionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                if (modelSetup != null) {
                    item(key = KEY_MODEL_SETUP) {
                        ModelSetupCard(
                            state = modelSetup,
                            onRetry = onRetryModelSetup,
                            onCancel = onCancelModelSetup,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                if (uiState.thisWeek.isNotEmpty()) {
                    item(key = KEY_THIS_WEEK) {
                        ThisWeekCard(
                            items = uiState.thisWeek,
                            today = uiState.today,
                            onItemClick = { onThreadClick(it.threadId) },
                            onAllClick = onAllTasksClick,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                if (!uiState.hasThreads) {
                    item {
                        EmptyState(
                            icon = if (hasPermission) Icons.AutoMirrored.Outlined.Chat else Icons.Outlined.NotificationsOff,
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(
                                if (hasPermission) R.string.home_empty_body else R.string.home_empty_no_permission_body
                            ),
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }
                    return@LazyColumn
                }

                item(key = KEY_FOLLOWED_HEADER) {
                    SectionHeader(
                        title = stringResource(R.string.home_followed),
                        trailing = autoSummaryHour?.let {
                            stringResource(R.string.home_auto_summary, formatHour(it))
                        }
                    )
                }
                if (uiState.followed.isEmpty()) {
                    item(key = KEY_FOLLOWED_EMPTY) {
                        Text(
                            text = stringResource(R.string.home_followed_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    threadRows(uiState.followed, uiState.now, onThreadClick, onSetFollowed, onSummarize)
                }

                if (uiState.others.isNotEmpty()) {
                    item(key = KEY_OTHERS_TOGGLE) {
                        OtherChatsRow(
                            count = uiState.others.size,
                            expanded = othersExpanded,
                            onToggle = { othersExpanded = !othersExpanded },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                        )
                    }
                    if (othersExpanded) {
                        threadRows(uiState.others, uiState.now, onThreadClick, onSetFollowed, onSummarize)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.threadRows(
    rows: List<ThreadRowState>,
    now: Long,
    onThreadClick: (String) -> Unit,
    onSetFollowed: (String, Boolean) -> Unit,
    onSummarize: (String) -> Unit
) {
    itemsIndexed(rows, key = { _, row -> row.threadId }) { index, row ->
        ThreadRow(
            row = row,
            now = now,
            shape = GroupedListDefaults.shape(index, rows.size),
            onClick = { onThreadClick(row.threadId) },
            onSetFollowed = { onSetFollowed(row.threadId, it) },
            onSummarize = { onSummarize(row.threadId) },
            modifier = Modifier
                .animateItem()
                .padding(horizontal = 12.dp)
                .padding(bottom = GroupedListDefaults.ItemSpacing)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSearchArea(color: Color, onSearchClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        SearchEntryBar(hint = stringResource(R.string.home_search_hint), onClick = onSearchClick)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun formatHour(hour: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(LocalConfiguration.current.locales[0])
        .format(LocalTime.of(hour.coerceIn(0, 23), 0))

@Composable
private fun ThisWeekCard(
    items: List<AgendaItem>,
    today: LocalDate,
    onItemClick: (AgendaItem) -> Unit,
    onAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.home_this_week),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                        .semantics { heading() }
                )
                val allDescription = stringResource(R.string.home_this_week_all_cd)
                TextButton(
                    onClick = onAllClick,
                    modifier = Modifier.semantics { contentDescription = allDescription }
                ) {
                    Text(stringResource(R.string.home_this_week_all))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            items.forEach { item ->
                ThisWeekRow(item = item, today = today, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun ThisWeekRow(item: AgendaItem, today: LocalDate, onClick: () -> Unit) {
    val date = AgendaSchedule.dateOf(item) ?: return
    val days = AgendaSchedule.daysUntil(item, today) ?: return
    val urgent = AgendaSchedule.isUrgent(item, today)
    val meta = listOfNotNull(
        item.threadName,
        relativeDay(days).takeIf { urgent },
        item.time?.let { agendaTime(it) }
    ).joinToString(" · ")
    val description = listOfNotNull(
        item.label,
        agendaDate(date),
        meta
    ).joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AgendaDateBadge(date = date, urgent = urgent, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OtherChatsRow(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedState = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Surface(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = expandedState },
        shape = RoundedCornerShape(20.dp),
        color = GroupedListDefaults.itemColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_other_chats, count),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.home_other_chats_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
