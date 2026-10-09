package com.summarizer.app.ui.screens.threads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.components.EmptyState
import com.summarizer.app.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ThreadListScreen(
    viewModel: ThreadListViewModel = hiltViewModel(),
    onThreadClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showOnlyFollowed by viewModel.showOnlyFollowed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context)) }

    // Check permission when screen resumes (user might grant it in Settings)
    LifecycleResumeEffect(Unit) {
        hasPermission = PermissionHelper.hasNotificationListenerPermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.threads_title),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show permission card if permission not granted
            AnimatedVisibility(
                visible = !hasPermission,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PermissionCard()
            }

            // Filter chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                FilterChip(
                    selected = !showOnlyFollowed,
                    onClick = { viewModel.toggleFilter() },
                    label = {
                        Text(
                            stringResource(
                                if (showOnlyFollowed) R.string.threads_filter_followed else R.string.threads_filter_all
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is ThreadListUiState.Loading -> {
                        LoadingState(modifier = Modifier.fillMaxSize())
                    }
                    is ThreadListUiState.Success -> {
                        if (state.threads.isEmpty()) {
                            ThreadListEmptyState(
                                hasPermission = hasPermission,
                                showOnlyFollowed = showOnlyFollowed
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(
                                    items = state.threads,
                                    key = { it.threadId }
                                ) { thread ->
                                    ThreadItem(
                                        thread = thread,
                                        showOnlyFollowed = showOnlyFollowed,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        ),
                                        onClick = { onThreadClick(thread.threadId) },
                                        onToggleFollow = { isFollowed ->
                                            viewModel.toggleFollowStatus(thread.threadId, isFollowed)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadListEmptyState(
    hasPermission: Boolean,
    showOnlyFollowed: Boolean
) {
    when {
        !hasPermission -> EmptyState(
            icon = Icons.Outlined.NotificationsOff,
            title = stringResource(R.string.threads_empty_title),
            body = stringResource(R.string.threads_empty_no_permission_body)
        )
        showOnlyFollowed -> EmptyState(
            icon = Icons.Outlined.StarOutline,
            title = stringResource(R.string.threads_empty_followed_title),
            body = stringResource(R.string.threads_empty_followed_body)
        )
        else -> EmptyState(
            icon = Icons.AutoMirrored.Outlined.Chat,
            title = stringResource(R.string.threads_empty_title),
            body = stringResource(R.string.threads_empty_body)
        )
    }
}

@Composable
fun ThreadItem(
    thread: Thread,
    showOnlyFollowed: Boolean,
    onClick: () -> Unit,
    onToggleFollow: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        ),
        colors = if (!thread.isFollowed && !showOnlyFollowed) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thread icon/avatar
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (thread.isFollowed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = thread.threadName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (thread.isFollowed) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Thread info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.threadName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (thread.isFollowed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.threads_item_meta,
                        pluralStringResource(R.plurals.message_count, thread.messageCount, thread.messageCount),
                        formatTimestamp(thread.lastMessageTimestamp)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Follow/unfollow star icon
            IconButton(
                onClick = { onToggleFollow(!thread.isFollowed) }
            ) {
                Icon(
                    imageVector = if (thread.isFollowed) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarOutline
                    },
                    contentDescription = stringResource(
                        if (thread.isFollowed) R.string.cd_unfollow else R.string.cd_follow
                    ),
                    tint = if (thread.isFollowed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp

    return when {
        diff < 60_000 -> stringResource(R.string.time_just_now)
        diff < 3600_000 -> (diff / 60_000).toInt().let { pluralStringResource(R.plurals.time_minutes_ago, it, it) }
        diff < 86400_000 -> (diff / 3600_000).toInt().let { pluralStringResource(R.plurals.time_hours_ago, it, it) }
        diff < 604800_000 -> (diff / 86400_000).toInt().let { pluralStringResource(R.plurals.time_days_ago, it, it) }
        else -> SimpleDateFormat("MMM dd", LocalConfiguration.current.locales[0]).format(Date(timestamp))
    }
}
