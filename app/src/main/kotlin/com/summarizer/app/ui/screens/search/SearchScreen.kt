package com.summarizer.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.ui.components.EmptyState
import com.summarizer.app.ui.components.SettingsRowIcon
import com.summarizer.app.ui.components.chatListTime
import com.summarizer.app.ui.components.rememberDateTimeFormats
import com.summarizer.app.util.ThreadNaming
import java.time.LocalDate

/**
 * Search across summaries, their tasks and messages, as a full-screen Material 3 search view:
 * recent searches before typing, filter chips (type and chat) and highlighted matches. Results
 * open in context: a summary or task opens the thread's summary, a message opens the thread.
 *
 * @param onSummaryClick opens the summary that matched in its thread; falls back to [onThreadClick]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackPressed: () -> Unit,
    onThreadClick: (String) -> Unit,
    onSummaryClick: ((threadId: String, summaryId: Long) -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val openThread = { threadId: String ->
        viewModel.onResultOpened()
        onThreadClick(threadId)
    }
    val openSummary = { threadId: String, summaryId: Long ->
        viewModel.onResultOpened()
        if (onSummaryClick != null) onSummaryClick(threadId, summaryId) else onThreadClick(threadId)
    }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                onSearch = { query ->
                    viewModel.submitSearch(query)
                    keyboard?.hide()
                },
                expanded = true,
                onExpandedChange = { expanded -> if (!expanded) onBackPressed() },
                placeholder = { Text(stringResource(R.string.search_bar_placeholder)) },
                leadingIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearSearch) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                }
            )
        },
        expanded = true,
        onExpandedChange = { expanded -> if (!expanded) onBackPressed() }
    ) {
        when (val state = uiState) {
            is SearchUiState.Empty -> if (recentSearches.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body)
                )
            } else {
                RecentSearches(
                    queries = recentSearches,
                    onPick = viewModel::useRecentSearch,
                    onRemove = viewModel::removeRecentSearch,
                    onClearAll = viewModel::clearRecentSearches
                )
            }

            is SearchUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            is SearchUiState.Success -> Column(modifier = Modifier.fillMaxSize()) {
                FilterChips(
                    state = state,
                    onToggleType = viewModel::toggleTypeFilter,
                    onToggleChat = viewModel::toggleChatFilter
                )
                if (state.isEmpty) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = stringResource(R.string.search_filter_empty)
                    )
                } else {
                    SearchResultsList(
                        state = state,
                        onSummaryClick = openSummary,
                        onMessageClick = openThread
                    )
                }
            }

            is SearchUiState.NoResults -> EmptyState(
                icon = Icons.Default.SearchOff,
                title = stringResource(R.string.search_no_results_title),
                body = stringResource(R.string.search_no_results_body, state.query)
            )

            SearchUiState.Error -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.search_error_title),
                body = stringResource(R.string.search_error_body),
                iconTint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private val bottomInsetPadding: PaddingValues
    @Composable get() = WindowInsets.navigationBars.asPaddingValues()

@Composable
private fun RecentSearches(
    queries: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = bottomInsetPadding
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_recent_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.search_recent_clear)) }
            }
        }
        items(queries, key = { it }) { query ->
            ListItem(
                headlineContent = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onRemove(query) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_recent_remove_cd, query)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickableRow { onPick(query) }
            )
        }
    }
}

@Composable
private fun FilterChips(
    state: SearchUiState.Success,
    onToggleType: (SearchResultType) -> Unit,
    onToggleChat: (String) -> Unit
) {
    val types = listOf(
        SearchResultType.SUMMARIES to R.string.search_filter_summaries,
        SearchResultType.TASKS to R.string.search_filter_tasks,
        SearchResultType.MESSAGES to R.string.search_filter_messages
    )
    val showChats = state.chats.size > 1
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(types, key = { it.first.name }) { (type, label) ->
            val selected = state.filter.type == type
            FilterChip(
                selected = selected,
                onClick = { onToggleType(type) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                }
            )
        }
        if (showChats) {
            item(key = "divider") { VerticalDivider(modifier = Modifier.height(24.dp)) }
            items(state.chats, key = { "chat-${it.threadId}" }) { chat ->
                FilterChip(
                    selected = state.filter.threadId == chat.threadId,
                    onClick = { onToggleChat(chat.threadId) },
                    label = {
                        Text(
                            text = ThreadNaming.displayName(chat.threadName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    state: SearchUiState.Success,
    onSummaryClick: (threadId: String, summaryId: Long) -> Unit,
    onMessageClick: (String) -> Unit
) {
    val query = state.query
    // Section headers only help when several kinds of results are mixed
    val showHeaders = state.filter.type == null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = bottomInsetPadding
    ) {
        if (state.summaries.isNotEmpty()) {
            if (showHeaders) item(key = "h-summaries") {
                SectionHeader(stringResource(R.string.search_section_summaries, state.summaries.size))
            }
            items(state.summaries, key = { "s-${it.id}" }) { summary ->
                SummaryResult(summary, query) { onSummaryClick(summary.threadId, summary.id) }
            }
        }
        if (state.tasks.isNotEmpty()) {
            if (showHeaders) item(key = "h-tasks") {
                SectionHeader(stringResource(R.string.search_section_tasks, state.tasks.size))
            }
            items(state.tasks, key = { "t-${it.summary.id}-${it.index}" }) { hit ->
                TaskResult(hit, query) { onSummaryClick(hit.summary.threadId, hit.summary.id) }
            }
        }
        if (state.messages.isNotEmpty()) {
            if (showHeaders) item(key = "h-messages") {
                SectionHeader(stringResource(R.string.search_section_messages, state.messages.size))
            }
            items(state.messages, key = { "m-${it.id}-${it.messageHash}" }) { message ->
                MessageResult(message, query) { onMessageClick(message.threadId) }
            }
        }
        item(key = "bottom") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SummaryResult(summary: Summary, query: String, onClick: () -> Unit) {
    ResultItem(
        icon = Icons.Default.AutoAwesome,
        headline = highlighted(ThreadNaming.displayName(summary.threadName), query),
        supporting = highlighted(SearchText.summarySnippet(summary, query), query),
        trailing = resultTime(summary.generatedAt),
        onClick = onClick
    )
}

@Composable
private fun TaskResult(hit: TaskHit, query: String, onClick: () -> Unit) {
    val chat = ThreadNaming.displayName(hit.summary.threadName)
    val due = hit.item.due?.let { formatIsoDate(it) }
    ResultItem(
        icon = Icons.Outlined.TaskAlt,
        headline = highlighted(hit.item.task, query),
        supporting = AnnotatedString(
            if (due != null) stringResource(R.string.search_task_due, chat, due) else chat
        ),
        trailing = if (hit.item.done) stringResource(R.string.search_task_done) else null,
        strikeThrough = hit.item.done,
        onClick = onClick
    )
}

@Composable
private fun MessageResult(message: Message, query: String, onClick: () -> Unit) {
    ResultItem(
        icon = Icons.AutoMirrored.Outlined.Chat,
        headline = highlighted(ThreadNaming.displayName(message.threadName), query),
        supporting = highlighted(
            stringResource(R.string.search_message_line, message.sender, SearchText.snippet(message.content, query)),
            query
        ),
        trailing = resultTime(message.timestamp),
        onClick = onClick
    )
}

@Composable
private fun ResultItem(
    icon: ImageVector,
    headline: AnnotatedString,
    supporting: AnnotatedString,
    trailing: String?,
    onClick: () -> Unit,
    strikeThrough: Boolean = false
) {
    ListItem(
        headlineContent = {
            Text(
                text = headline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (strikeThrough) TextDecoration.LineThrough else null
            )
        },
        supportingContent = {
            Text(text = supporting, maxLines = 3, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { SettingsRowIcon(icon) },
        trailingContent = trailing?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick)
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this
        .fillMaxWidth()
        .clickable(onClick = onClick)

/** [text] with every occurrence of [query] emphasized. */
@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val style = SpanStyle(
        background = colors.tertiaryContainer,
        color = colors.onTertiaryContainer,
        fontWeight = FontWeight.SemiBold
    )
    return remember(text, query, style) {
        buildAnnotatedString {
            var last = 0
            SearchText.matchRanges(text, query).forEach { range ->
                append(text.substring(last, range.first))
                withStyle(style) { append(text.substring(range.first, range.last + 1)) }
                last = range.last + 1
            }
            append(text.substring(last))
        }
    }
}

/** When a result was written, chat-list style: "17:42" today, "Yesterday", "Mon", then "Oct 12". */
@Composable
private fun resultTime(timestamp: Long): String = chatListTime(timestamp, System.currentTimeMillis())

@Composable
private fun formatIsoDate(isoDate: String): String? {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
    return rememberDateTimeFormats().weekdayDate(date)
}
