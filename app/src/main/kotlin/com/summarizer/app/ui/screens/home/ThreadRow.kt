package com.summarizer.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.ui.components.CountBadge
import com.summarizer.app.ui.components.GroupedListDefaults
import com.summarizer.app.ui.components.InfoChip
import com.summarizer.app.ui.components.ThreadAvatar
import com.summarizer.app.ui.components.chatListTime
import com.summarizer.app.ui.components.relativeTimeAgo

/**
 * One chat on the home screen: avatar, name and time of the last message, a teaser (the latest
 * summary's overview, else the last message) and a meta line with open tasks, how fresh the
 * summary is and how many messages arrived since. While the chat is being summarized the
 * teaser becomes a progress bar. Long-press (or a TalkBack action) follows or unfollows it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadRow(
    row: ThreadRowState,
    now: Long,
    shape: Shape,
    onClick: () -> Unit,
    onSetFollowed: (Boolean) -> Unit,
    onSummarize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overview = row.overview
    val thread = overview.thread
    val summary = overview.latestSummary
    val newCount = overview.newSinceSummary
    val overviewText = summary?.overview?.takeIf { it.isNotBlank() }
    val lastMessage = overview.lastMessageText?.takeIf { it.isNotBlank() }?.let { text ->
        overview.lastMessageSender?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.thread_row_sender_message, it, text) }
            ?: text
    }
    val time = chatListTime(thread.lastMessageTimestamp, now)
    val freshness = summary?.let { relativeTimeAgo(it.generatedAt, now) }

    var menuOpen by remember { mutableStateOf(false) }
    val followLabel = stringResource(if (thread.isFollowed) R.string.thread_row_unfollow else R.string.thread_row_follow)
    val description = listOfNotNull(
        thread.threadName,
        stringResource(R.string.thread_row_cd_last_message_time, time),
        when {
            newCount <= 0 -> null
            summary == null -> pluralStringResource(R.plurals.thread_row_cd_new_messages_no_summary, newCount, newCount)
            else -> pluralStringResource(R.plurals.thread_row_cd_new_messages, newCount, newCount)
        },
        overview.openTasks.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.thread_row_cd_open_tasks, it, it) },
        when {
            row.isGenerating -> stringResource(R.string.thread_row_summarizing)
            row.hasFailed -> stringResource(R.string.thread_row_failed)
            else -> null
        },
        when {
            overviewText != null -> stringResource(R.string.thread_row_cd_summary, overviewText)
            lastMessage != null -> stringResource(R.string.thread_row_cd_last_message, lastMessage)
            else -> null
        },
        freshness?.let { stringResource(R.string.thread_row_cd_summarized, it) }
    ).joinToString(", ")

    Box(modifier = modifier) {
        Surface(shape = shape, color = GroupedListDefaults.itemColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuOpen = true },
                        onLongClickLabel = stringResource(R.string.thread_row_options)
                    )
                    .semantics {
                        contentDescription = description
                        customActions = listOf(
                            CustomAccessibilityAction(followLabel) {
                                onSetFollowed(!thread.isFollowed)
                                true
                            }
                        )
                    }
                    .padding(start = 12.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThreadAvatar(name = thread.threadName, modifier = Modifier.clearAndSetSemantics { })
                Column(modifier = Modifier.weight(1f)) {
                    NameAndTime(name = thread.threadName, time = time, hasNew = newCount > 0)
                    // No animated bar while summarizing: the meta line says so, and on-device
                    // generation keeps the GPU so busy that every animation frame stalls the UI
                    when {
                        overviewText != null -> Teaser(text = overviewText, isSummary = true)
                        lastMessage != null -> Teaser(text = lastMessage, isSummary = false)
                    }
                    MetaLine(
                        row = row,
                        freshness = freshness,
                        onSummarize = onSummarize
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(followLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = if (thread.isFollowed) Icons.Outlined.StarOutline else Icons.Filled.Star,
                        contentDescription = null
                    )
                },
                onClick = {
                    menuOpen = false
                    onSetFollowed(!thread.isFollowed)
                }
            )
        }
    }
}

@Composable
private fun NameAndTime(name: String, time: String, hasNew: Boolean) {
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (hasNew) FontWeight.Medium else FontWeight.Normal,
            color = if (hasNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Teaser(text: String, isSummary: Boolean) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSummary) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text.lineSequence().first { it.isNotBlank() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetaLine(
    row: ThreadRowState,
    freshness: String?,
    onSummarize: () -> Unit
) {
    val overview = row.overview
    val newCount = overview.newSinceSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                row.isGenerating -> MetaText(
                    text = stringResource(R.string.thread_row_summarizing),
                    icon = { MetaIcon(Icons.Filled.AutoAwesome) }
                )
                row.hasFailed -> MetaText(
                    text = stringResource(R.string.thread_row_failed),
                    color = MaterialTheme.colorScheme.error,
                    icon = { MetaIcon(Icons.Outlined.ErrorOutline, tint = MaterialTheme.colorScheme.error) }
                )
                overview.latestSummary == null -> MetaText(text = stringResource(R.string.thread_row_no_summary))
                else -> {
                    if (overview.openTasks > 0) {
                        InfoChip(
                            text = pluralStringResource(R.plurals.thread_row_open_tasks, overview.openTasks, overview.openTasks),
                            icon = Icons.Outlined.TaskAlt
                        )
                    }
                    if (newCount == 0) {
                        MetaText(
                            text = stringResource(R.string.thread_row_up_to_date),
                            icon = { MetaIcon(Icons.Outlined.CheckCircle) }
                        )
                    } else if (freshness != null) {
                        MetaText(text = freshness)
                    }
                }
            }
        }
        if (!row.isGenerating && overview.latestSummary == null) {
            FilledTonalButton(
                onClick = onSummarize,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.thread_row_summarize), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (newCount > 0) {
            CountBadge(count = newCount, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun MetaText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetaIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}
