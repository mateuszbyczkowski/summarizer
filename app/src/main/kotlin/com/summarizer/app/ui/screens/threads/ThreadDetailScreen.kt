package com.summarizer.app.ui.screens.threads

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    threadId: String,
    onBackClick: () -> Unit,
    viewModel: ThreadDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val threadName = when (val state = uiState) {
        is ThreadDetailUiState.Success -> state.messages.firstOrNull()?.threadName ?: "Thread"
        else -> "Thread"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(threadName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No messages in this thread",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.messages, key = { it.id }) { message ->
                                    MessageItem(message = message)
                                }
                            }

                            // Placeholder for "Summarize Now" button (Week 5)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = 3.dp
                            ) {
                                Button(
                                    onClick = { /* TODO: Implement summarization in Week 5 */ },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    enabled = false
                                ) {
                                    Text("Summarize Now (Coming Soon)")
                                }
                            }
                        }
                    }
                }
                is ThreadDetailUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error loading messages",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val containerColor = when {
        message.isDeleted -> MaterialTheme.colorScheme.errorContainer
        message.messageType == MessageType.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        message.isDeleted -> MaterialTheme.colorScheme.onErrorContainer
        message.messageType == MessageType.SYSTEM -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (message.messageType != MessageType.TEXT) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getMessageTypeLabel(message.messageType),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.content,
                style = if (message.isDeleted || message.messageType == MessageType.SYSTEM) {
                    MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = contentColor
            )
        }
    }
}

private fun getMessageTypeLabel(type: MessageType): String {
    return when (type) {
        MessageType.IMAGE -> "📷 Image"
        MessageType.VIDEO -> "📹 Video"
        MessageType.AUDIO -> "🎵 Audio"
        MessageType.DOCUMENT -> "📄 Document"
        MessageType.LOCATION -> "📍 Location"
        MessageType.CONTACT -> "👤 Contact"
        MessageType.STICKER -> "Sticker"
        MessageType.DELETED -> "🚫 Deleted"
        MessageType.SYSTEM -> "System"
        else -> ""
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
