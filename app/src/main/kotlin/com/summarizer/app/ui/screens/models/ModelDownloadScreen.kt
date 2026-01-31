package com.summarizer.app.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onModelSelected: (AIModel) -> Unit,
    onSkip: () -> Unit
) {
    // Sample models for UI preview
    val sampleModels = remember {
        listOf(
            AIModel(
                id = "tinyllama-1.1b",
                name = "TinyLlama 1.1B",
                description = "Small and fast model, perfect for budget devices. Good for basic summaries.",
                sizeInMB = 700,
                downloadUrl = "https://example.com/tinyllama",
                isRecommended = true,
                minimumRAM = 4,
                estimatedSpeed = "Fast"
            ),
            AIModel(
                id = "phi-2-2.7b",
                name = "Phi-2 2.7B",
                description = "More capable model with better understanding. Requires more RAM.",
                sizeInMB = 1800,
                downloadUrl = "https://example.com/phi2",
                isRecommended = false,
                minimumRAM = 6,
                estimatedSpeed = "Medium"
            ),
            AIModel(
                id = "gemma-2b",
                name = "Gemma 2B",
                description = "Google's efficient model, balanced performance and speed.",
                sizeInMB = 1400,
                downloadUrl = "https://example.com/gemma",
                isRecommended = false,
                minimumRAM = 4,
                estimatedSpeed = "Fast"
            )
        )
    }

    var downloadStates by remember {
        mutableStateOf(
            sampleModels.associate { it.id to ModelDownloadState(it.id) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download AI Model") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Choose Your AI Model",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select and download an AI model to power your summaries. All processing happens on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Model list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sampleModels) { model ->
                    ModelCard(
                        model = model,
                        downloadState = downloadStates[model.id] ?: ModelDownloadState(model.id),
                        onDownloadClick = { /* TODO: Implement in Week 4 */ },
                        onSelectClick = { onModelSelected(model) }
                    )
                }
            }

            // Skip button
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Skip for Now")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCard(
    model: AIModel,
    downloadState: ModelDownloadState,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isRecommended) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with name and recommended badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (model.isRecommended) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Recommended") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Specifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModelSpec(
                    icon = Icons.Default.Storage,
                    label = "${model.sizeInMB} MB"
                )
                ModelSpec(
                    icon = Icons.Default.Memory,
                    label = "${model.minimumRAM} GB RAM"
                )
                ModelSpec(
                    icon = Icons.Default.Speed,
                    label = model.estimatedSpeed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download button or progress
            when (downloadState.status) {
                DownloadStatus.NOT_STARTED -> {
                    if (model.isDownloaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Downloaded",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(onClick = onSelectClick) {
                                Text("Use This Model")
                            }
                        }
                    } else {
                        Button(
                            onClick = onDownloadClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download (${model.sizeInMB} MB)")
                        }
                    }
                }
                DownloadStatus.DOWNLOADING -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading...",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${(downloadState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = downloadState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    Button(
                        onClick = onSelectClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use This Model")
                    }
                }
                DownloadStatus.FAILED -> {
                    Column {
                        Text(
                            text = "Download failed: ${downloadState.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onDownloadClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry Download")
                        }
                    }
                }
                DownloadStatus.PAUSED -> {
                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resume Download")
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSpec(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
