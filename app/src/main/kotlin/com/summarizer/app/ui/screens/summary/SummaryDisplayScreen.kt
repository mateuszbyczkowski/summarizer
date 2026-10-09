package com.summarizer.app.ui.screens.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryDisplayScreen(
    onBackClick: () -> Unit,
    viewModel: SummaryDisplayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val threadName by viewModel.threadName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (threadName.isBlank()) {
                    stringResource(R.string.summary_title_fallback)
                } else {
                    stringResource(R.string.summary_title, threadName)
                },
                onBackClick = onBackClick,
                actions = {
                    if (uiState is SummaryUiState.Success) {
                        IconButton(onClick = { viewModel.generateSummary() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.summary_regenerate)
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
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(200))
                },
                label = "summaryStateTransition"
            ) { state ->
                when (state) {
                    is SummaryUiState.Loading -> Unit
                    is SummaryUiState.Initial -> {
                        InitialPrompt(
                            onGenerateClick = { viewModel.generateSummary() }
                        )
                    }
                    is SummaryUiState.Generating -> {
                        GeneratingIndicator()
                    }
                    is SummaryUiState.Success -> {
                        SummaryContent(summary = state.summary)
                    }
                    is SummaryUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetryClick = { viewModel.retry() },
                            onDismissClick = { viewModel.dismissError() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialPrompt(onGenerateClick: () -> Unit) {
    EmptyState(
        icon = Icons.Default.AutoAwesome,
        title = stringResource(R.string.summary_initial_title),
        body = stringResource(R.string.summary_initial_body),
        action = {
            Button(onClick = onGenerateClick) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.summary_generate))
            }
        }
    )
}

@Composable
private fun GeneratingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.summary_generating),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.summary_generating_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryContent(summary: Summary) {
    var showRawOutput by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Metadata card
            item {
                SummaryMetadataCard(summary = summary)
            }

            // Raw AI Response (for debugging) - Hidden by default
            if (showRawOutput && !summary.rawAIResponse.isNullOrBlank()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.summary_section_raw_output),
                        icon = "🤖"
                    ) {
                        Text(
                            text = summary.rawAIResponse,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Overview section
            if (summary.overview.isNotBlank()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.summary_section_overview),
                        icon = "📝"
                    ) {
                        Text(
                            text = summary.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Key Topics section
            if (summary.keyTopics.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.summary_section_key_topics),
                        icon = "💡"
                    ) {
                        summary.keyTopics.forEach { topic ->
                            BulletPoint(text = topic)
                        }
                    }
                }
            }

            // Action Items section
            if (summary.actionItems.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.summary_section_action_items),
                        icon = "✅"
                    ) {
                        summary.actionItems.forEach { item ->
                            ActionItemCard(
                                task = item.task,
                                assignedTo = item.assignedTo,
                                priority = item.priority
                            )
                        }
                    }
                }
            }

            // Announcements section
            if (summary.announcements.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.summary_section_announcements),
                        icon = "📢"
                    ) {
                        summary.announcements.forEach { announcement ->
                            BulletPoint(text = announcement)
                        }
                    }
                }
            }

            // Participant Highlights section
            if (summary.participantHighlights.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.summary_section_participants),
                        icon = "👤"
                    ) {
                        summary.participantHighlights.forEach { highlight ->
                            ParticipantHighlightCard(
                                participant = highlight.participant,
                                contribution = highlight.contribution
                            )
                        }
                    }
                }
            }
        }

        // Toggle button for raw output at the bottom
        if (!summary.rawAIResponse.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                TextButton(
                    onClick = { showRawOutput = !showRawOutput },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(if (showRawOutput) R.string.summary_hide_raw_output else R.string.summary_show_raw_output),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetadataCard(summary: Summary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.threadName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.summary_generated_at, formatDate(summary.generatedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = formatTimeRange(summary.startTimestamp, summary.endTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$icon $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionItemCard(
    task: String,
    assignedTo: String?,
    priority: String
) {
    val colors = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (priority.lowercase()) {
        "high" -> colors.errorContainer to colors.onErrorContainer
        "medium" -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = task,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (assignedTo != null) {
                    Text(
                        text = stringResource(R.string.summary_assigned_to, assignedTo),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = stringResource(R.string.summary_priority, priority.uppercase()),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ParticipantHighlightCard(
    participant: String,
    contribution: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = participant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = contribution,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.summary_error_title),
        body = message,
        iconTint = MaterialTheme.colorScheme.error,
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRetryClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onDismissClick) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimeRange(startTimestamp: Long, endTimestamp: Long): String {
    val start = Date(startTimestamp)
    val end = Date(endTimestamp)

    val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    val startDate = dateFormatter.format(start)
    val endDate = dateFormatter.format(end)

    return if (startDate == endDate) {
        // Same day - show date range with times
        "$startDate ${timeFormatter.format(start)} - ${timeFormatter.format(end)}"
    } else {
        // Different days - show full date range
        "$startDate - $endDate"
    }
}
