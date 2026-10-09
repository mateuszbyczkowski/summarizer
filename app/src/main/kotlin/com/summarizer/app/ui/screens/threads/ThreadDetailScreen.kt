package com.summarizer.app.ui.screens.threads

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.summarizer.app.R
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ThreadDetailScreen(
    threadId: String,
    onBackClick: () -> Unit,
    onSummarizeClick: (threadId: String, threadName: String) -> Unit,
    viewModel: ThreadDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val threadSettings by viewModel.threadSettings.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    val threadName = when (val state = uiState) {
        is ThreadDetailUiState.Success -> state.messages.firstOrNull()?.threadName ?: thread?.threadName
        else -> thread?.threadName
    } ?: stringResource(R.string.thread_detail_fallback_title)

    Scaffold(
        topBar = {
            AppTopBar(
                title = threadName,
                onBackClick = onBackClick,
                actions = {
                    thread?.let { currentThread ->
                        IconButton(
                            onClick = { viewModel.toggleFollowStatus(!currentThread.isFollowed) }
                        ) {
                            Icon(
                                imageVector = if (currentThread.isFollowed) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarOutline
                                },
                                contentDescription = stringResource(
                                    if (currentThread.isFollowed) R.string.cd_unfollow else R.string.cd_follow
                                ),
                                tint = if (currentThread.isFollowed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }

                    // Three-dot menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            Text(
                                text = stringResource(R.string.thread_detail_mode_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            SummarizationModeItem(
                                title = R.string.thread_detail_mode_incremental,
                                description = R.string.thread_detail_mode_incremental_description,
                                selected = threadSettings?.summarizationMode == SummarizationMode.INCREMENTAL,
                                onClick = {
                                    viewModel.updateSummarizationMode(SummarizationMode.INCREMENTAL)
                                    showMenu = false
                                }
                            )

                            SummarizationModeItem(
                                title = R.string.thread_detail_mode_full,
                                description = R.string.thread_detail_mode_full_description,
                                selected = threadSettings?.summarizationMode == SummarizationMode.FULL,
                                onClick = {
                                    viewModel.updateSummarizationMode(SummarizationMode.FULL)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ThreadDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is ThreadDetailUiState.Success -> {
                    if (state.messages.isEmpty()) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.Chat,
                            title = stringResource(R.string.thread_detail_empty_title),
                            body = stringResource(R.string.thread_detail_empty_body)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.messages, key = { it.id }) { message ->
                                    MessageItem(message = message)
                                }
                            }

                            // Summarize Now button
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = 3.dp
                            ) {
                                Button(
                                    onClick = { onSummarizeClick(threadId, threadName) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(stringResource(R.string.thread_detail_summarize))
                                }
                            }
                        }
                    }
                }
                is ThreadDetailUiState.Error -> {
                    EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.thread_detail_error_title),
                        body = state.message,
                        iconTint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarizationModeItem(
    @StringRes title: Int,
    @StringRes description: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick,
        leadingIcon = {
            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    )
}

@Composable
fun MessageItem(message: Message) {
    val containerColor = when {
        message.isDeleted -> MaterialTheme.colorScheme.errorContainer
        message.messageType == MessageType.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        message.isDeleted -> MaterialTheme.colorScheme.onErrorContainer
        message.messageType == MessageType.SYSTEM -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    messageTypeLabel(message.messageType)?.let { label ->
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.content,
                style = if (message.isDeleted || message.messageType == MessageType.SYSTEM) {
                    MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                } else {
                    MaterialTheme.typography.bodyMedium
                }
            )
        }
    }
}

@StringRes
private fun messageTypeLabel(type: MessageType): Int? = when (type) {
    MessageType.IMAGE -> R.string.message_type_image
    MessageType.VIDEO -> R.string.message_type_video
    MessageType.AUDIO -> R.string.message_type_audio
    MessageType.DOCUMENT -> R.string.message_type_document
    MessageType.LOCATION -> R.string.message_type_location
    MessageType.CONTACT -> R.string.message_type_contact
    MessageType.STICKER -> R.string.message_type_sticker
    MessageType.DELETED -> R.string.message_type_deleted
    MessageType.SYSTEM -> R.string.message_type_system
    MessageType.TEXT, MessageType.UNKNOWN -> null
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
