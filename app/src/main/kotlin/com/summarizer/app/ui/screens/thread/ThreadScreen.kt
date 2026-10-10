package com.summarizer.app.ui.screens.thread

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.ui.components.openAddToCalendar
import com.summarizer.app.ui.components.rememberNotificationPermissionRequest
import com.summarizer.app.util.SummaryClipboard
import com.summarizer.app.util.SummaryFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long sections of a freshly generated summary keep their fade-in. */
private const val REVEAL_WINDOW_MS = 2_000L

/**
 * One screen per chat: its summary (with history, generation progress, dates, tasks) and its
 * messages, in two tabs. The main action updates the summary with the new messages.
 */
@Composable
fun ThreadScreen(
    onBackClick: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        ThreadUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ThreadUiState.Ready -> ThreadContent(
            state = state,
            requestedTab = viewModel.requestedTab,
            onBackClick = onBackClick,
            actions = ThreadActions(
                onUpdate = viewModel::update,
                onFullResummary = viewModel::fullResummary,
                onCancel = viewModel::cancelGeneration,
                onDismissError = viewModel::dismissError,
                onSetMode = viewModel::setMode,
                onSetFollowed = viewModel::setFollowed,
                onSelectSummary = viewModel::selectSummary,
                onSetTaskDone = viewModel::setTaskDone,
                onRate = viewModel::rate
            )
        )
    }
}

/** What the user can do on the thread screen. */
class ThreadActions(
    val onUpdate: () -> Unit,
    val onFullResummary: () -> Unit,
    val onCancel: () -> Unit,
    val onDismissError: () -> Unit,
    val onSetMode: (SummarizationMode) -> Unit,
    val onSetFollowed: (Boolean) -> Unit,
    val onSelectSummary: (Long) -> Unit,
    val onSetTaskDone: (summaryId: Long, index: Int, done: Boolean) -> Unit,
    val onRate: (Summary, Int) -> Unit
)

/** Callbacks of the summary tab that need the screen (snackbars, other apps). */
class SummaryCallbacks(
    val onTaskChecked: (Summary, Int, Boolean) -> Unit,
    val onAddToCalendar: (Summary, KeyDate) -> Unit,
    val onCopy: (Summary) -> Unit,
    val onShare: (Summary) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadContent(
    state: ThreadUiState.Ready,
    requestedTab: ThreadTab?,
    onBackClick: () -> Unit,
    actions: ThreadActions
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    val initialTab = requestedTab ?: if (state.latest != null) ThreadTab.SUMMARY else ThreadTab.MESSAGES
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { ThreadTab.entries.size }
    val summaryListState = rememberLazyListState()
    val messagesListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (MessageTimeline.initialScrollIndex(state.timeline) - 1).coerceAtLeast(0)
    )
    val activeList = if (pagerState.currentPage == ThreadTab.SUMMARY.ordinal) summaryListState else messagesListState

    // Plain surface that tints once content scrolls under it, tabs included
    val barColor by animateColorAsState(
        targetValue = if (activeList.canScrollBackward) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "threadBarColor"
    )

    // The FAB collapses to its icon while scrolling down and expands again on the way up, at
    // the top of the list, and when more messages arrive
    var fabExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(state.newMessages) { fabExpanded = true }
    val fabScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) fabExpanded = false else if (available.y > 1f) fabExpanded = true
                return Offset.Zero
            }
        }
    }

    // Sections of a summary that was just generated fade in
    var revealSummaryId by remember { mutableStateOf<Long?>(null) }
    var latestWhenStarted by remember { mutableStateOf<Long?>(null) }
    var wasGenerating by remember { mutableStateOf(false) }
    LaunchedEffect(state.isGenerating, state.latest?.id) {
        if (state.isGenerating && !wasGenerating) latestWhenStarted = state.latest?.id ?: -1L
        if (!state.isGenerating && wasGenerating) {
            val latestId = state.latest?.id
            if (latestId != null && latestId != latestWhenStarted) revealSummaryId = latestId
        }
        wasGenerating = state.isGenerating
    }
    LaunchedEffect(revealSummaryId) {
        if (revealSummaryId != null) {
            delay(REVEAL_WINDOW_MS)
            revealSummaryId = null
        }
    }

    val startGeneration: (full: Boolean) -> Unit = { full ->
        requestNotificationPermission()
        if (full) actions.onFullResummary() else actions.onUpdate()
        scope.launch {
            summaryListState.scrollToItem(0)
            pagerState.animateScrollToPage(ThreadTab.SUMMARY.ordinal)
        }
    }

    val doneMessage = stringResource(R.string.tasks_done_snackbar)
    val undoLabel = stringResource(R.string.tasks_undo)
    val noCalendarMessage = stringResource(R.string.tasks_no_calendar_app)
    val shareTitle = stringResource(R.string.thread_share)
    val callbacks = SummaryCallbacks(
        onTaskChecked = { summary, index, done ->
            actions.onSetTaskDone(summary.id, index, done)
            if (done) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(doneMessage, undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) actions.onSetTaskDone(summary.id, index, false)
                }
            }
        },
        onAddToCalendar = { summary, keyDate ->
            val description = listOfNotNull(keyDate.detail, summary.threadName).joinToString("\n")
            if (!context.openAddToCalendar(keyDate.label, description, keyDate.date, keyDate.time)) {
                scope.launch { snackbarHostState.showSnackbar(noCalendarMessage) }
            }
        },
        onCopy = { summary -> SummaryClipboard.copy(context, SummaryFormatter.toPlainText(context, summary)) },
        onShare = { summary ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, SummaryFormatter.toPlainText(context, summary))
            context.startActivity(Intent.createChooser(send, shareTitle))
        }
    )

    val showFab = !state.isGenerating && state.messages.isNotEmpty() && (state.latest == null || state.newMessages > 0)

    Scaffold(
        modifier = Modifier.nestedScroll(fabScroll),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                ThreadTopBar(
                    state = state,
                    color = barColor,
                    onBackClick = onBackClick,
                    onShare = state.shown?.let { shown -> { callbacks.onShare(shown) } },
                    onFullResummary = { startGeneration(true) },
                    onSetMode = actions.onSetMode,
                    onSetFollowed = actions.onSetFollowed
                )
                ThreadTabs(
                    selected = ThreadTab.entries[pagerState.currentPage],
                    newMessages = state.newMessages,
                    color = barColor,
                    onSelect = { tab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) } }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                UpdateFab(
                    hasSummary = state.latest != null,
                    newMessages = state.newMessages,
                    expanded = fabExpanded || !activeList.canScrollBackward,
                    onClick = { startGeneration(false) }
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            beyondViewportPageCount = 1
        ) { page ->
            when (ThreadTab.entries[page]) {
                ThreadTab.SUMMARY -> SummaryTab(
                    state = state,
                    listState = summaryListState,
                    bottomPadding = padding.calculateBottomPadding(),
                    revealSummaryId = revealSummaryId,
                    actions = actions,
                    callbacks = callbacks,
                    onRetry = { startGeneration(false) }
                )
                ThreadTab.MESSAGES -> MessagesTab(
                    timeline = state.timeline,
                    today = state.today,
                    listState = messagesListState,
                    bottomPadding = padding.calculateBottomPadding()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTopBar(
    state: ThreadUiState.Ready,
    color: Color,
    onBackClick: () -> Unit,
    onShare: (() -> Unit)?,
    onFullResummary: () -> Unit,
    onSetMode: (SummarizationMode) -> Unit,
    onSetFollowed: (Boolean) -> Unit
) {
    val title = state.threadName.ifBlank { stringResource(R.string.thread_fallback_title) }
    val subtitle = if (state.isGenerating) {
        stringResource(R.string.thread_subtitle_summarizing)
    } else {
        threadSubtitle(state)
    }
    TopAppBar(
        title = {
            Column {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.thread_share))
                }
            }
            ThreadOverflowMenu(
                mode = state.mode,
                followed = state.thread?.isFollowed,
                canResummarize = !state.isGenerating && state.messages.isNotEmpty(),
                onFullResummary = onFullResummary,
                onSetMode = onSetMode,
                onSetFollowed = onSetFollowed
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = color, scrolledContainerColor = color)
    )
}

/** "48 messages · 10–13.10", or null for an empty chat. */
@Composable
private fun threadSubtitle(state: ThreadUiState.Ready): String? {
    val count = state.messages.size
    val range = ThreadSummaryRules.dayRange(state.messages.map { it.timestamp }) ?: return null
    return stringResource(
        R.string.thread_subtitle,
        pluralStringResource(R.plurals.thread_message_count, count, count),
        ThreadSummaryRules.formatDayRange(range.first, range.second)
    )
}

@Composable
private fun ThreadOverflowMenu(
    mode: SummarizationMode,
    followed: Boolean?,
    canResummarize: Boolean,
    onFullResummary: () -> Unit,
    onSetMode: (SummarizationMode) -> Unit,
    onSetFollowed: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    MenuText(
                        title = stringResource(R.string.thread_menu_full_resummary),
                        description = stringResource(R.string.thread_menu_full_resummary_description)
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                enabled = canResummarize,
                onClick = {
                    expanded = false
                    onFullResummary()
                }
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.thread_menu_mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ModeItem(
                title = stringResource(R.string.thread_mode_incremental),
                description = stringResource(R.string.thread_mode_incremental_description),
                selected = mode == SummarizationMode.INCREMENTAL,
                onClick = {
                    expanded = false
                    onSetMode(SummarizationMode.INCREMENTAL)
                }
            )
            ModeItem(
                title = stringResource(R.string.thread_mode_full),
                description = stringResource(R.string.thread_mode_full_description),
                selected = mode == SummarizationMode.FULL,
                onClick = {
                    expanded = false
                    onSetMode(SummarizationMode.FULL)
                }
            )
            if (followed != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (followed) R.string.thread_menu_unfollow else R.string.thread_menu_follow))
                    },
                    leadingIcon = {
                        Icon(if (followed) Icons.Outlined.Star else Icons.Outlined.StarOutline, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSetFollowed(!followed)
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuText(title: String, description: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeItem(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { MenuText(title, description) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTabs(
    selected: ThreadTab,
    newMessages: Int,
    color: Color,
    onSelect: (ThreadTab) -> Unit
) {
    val newDescription = pluralStringResource(R.plurals.thread_tab_new_cd, newMessages, newMessages)
    PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = color) {
        ThreadTab.entries.forEach { tab ->
            val label = stringResource(
                when (tab) {
                    ThreadTab.SUMMARY -> R.string.thread_tab_summary
                    ThreadTab.MESSAGES -> R.string.thread_tab_messages
                }
            )
            val showBadge = tab == ThreadTab.MESSAGES && newMessages > 0
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (showBadge) Modifier.semantics { contentDescription = "$label, $newDescription" } else Modifier,
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label)
                        if (showBadge) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.clearAndSetSemantics { }
                            ) {
                                Text(if (newMessages > 999) "999+" else newMessages.toString())
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun UpdateFab(hasSummary: Boolean, newMessages: Int, expanded: Boolean, onClick: () -> Unit) {
    val description = if (hasSummary) {
        pluralStringResource(R.plurals.thread_fab_update_cd, newMessages, newMessages)
    } else {
        stringResource(R.string.thread_fab_summarize)
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(if (hasSummary) Icons.Filled.Refresh else Icons.Filled.AutoAwesome, contentDescription = null) },
        text = {
            if (hasSummary) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.thread_fab_update))
                    Text(
                        text = pluralStringResource(R.plurals.thread_fab_new, newMessages, newMessages),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            } else {
                Text(stringResource(R.string.thread_fab_summarize))
            }
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick { onClick(); true }
        }
    )
}
