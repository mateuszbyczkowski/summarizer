package com.summarizer.app.ui.screens.models

import android.app.ActivityManager
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.components.StatusBadge
import com.summarizer.app.ui.components.formatModelSize
import com.summarizer.app.util.StorageHelper
import kotlin.math.roundToInt

@Composable
fun ModelDownloadScreen(
    viewModel: ModelDownloadViewModel = hiltViewModel(),
    onModelSelected: (AIModel) -> Unit,
    onSkip: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onStorageClick: (() -> Unit)? = null
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val isWiFiOnly by viewModel.isWiFiOnly.collectAsStateWithLifecycle()
    val deviceRamGb = rememberDeviceRamGb()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.models_title),
                onBackClick = onBackClick,
                actions = {
                    if (onStorageClick != null) {
                        IconButton(onClick = onStorageClick) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = stringResource(R.string.models_storage_settings)
                            )
                        }
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
            // Header with WiFi toggle
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.models_heading),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isWiFiOnly,
                        onClick = { viewModel.toggleWiFiOnly() },
                        label = {
                            Text(
                                stringResource(
                                    if (isWiFiOnly) R.string.models_wifi_only else R.string.models_any_network
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isWiFiOnly) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.models_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelSpec(
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.models_device_ram, deviceRamGb)
                )
            }

            // Model list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        deviceRamGb = deviceRamGb,
                        downloadState = downloadStates[model.id] ?: ModelDownloadState(model.id),
                        onDownloadClick = { viewModel.downloadModel(model) },
                        onPauseClick = { viewModel.pauseDownload(model.id) },
                        onResumeClick = { viewModel.resumeDownload(model.id) },
                        onSelectClick = {
                            viewModel.preloadModel(model)  // Pre-load model in background
                            onModelSelected(model)
                        },
                        onDeleteClick = { viewModel.deleteModel(model.id) }
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
                Text(stringResource(R.string.models_skip))
            }
        }
    }
}

/**
 * Total device RAM rounded to whole GB. [ActivityManager.MemoryInfo.totalMem] excludes memory
 * reserved by the kernel, so rounding the decimal value lands on the advertised size
 * (e.g. ~7.9e9 bytes on an "8 GB" phone).
 */
@Composable
private fun rememberDeviceRamGb(): Int {
    val context = LocalContext.current
    return remember(context) {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        (memoryInfo.totalMem / 1_000_000_000.0).roundToInt()
    }
}

@Composable
fun ModelCard(
    model: AIModel,
    deviceRamGb: Int,
    downloadState: ModelDownloadState,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onSelectClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val exceedsDeviceRam = model.minimumRAM > deviceRamGb

    // Animate progress changes smoothly
    val animatedProgress by animateFloatAsState(
        targetValue = downloadState.progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 500,
            easing = LinearOutSlowInEasing
        ),
        label = "downloadProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isRecommended) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (model.isRecommended || exceedsDeviceRam) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.isRecommended) {
                        StatusBadge(
                            text = stringResource(R.string.badge_recommended),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = Icons.Default.Star
                        )
                    }
                    if (exceedsDeviceRam) {
                        StatusBadge(
                            text = stringResource(R.string.models_badge_needs_ram, model.minimumRAM),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            icon = Icons.Default.Warning
                        )
                    }
                }
            }

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )

            // Specifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModelSpec(
                    icon = Icons.Default.Storage,
                    label = formatModelSize(model.sizeInMB)
                )
                ModelSpec(
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.models_ram_spec, model.minimumRAM)
                )
                ModelSpec(
                    icon = Icons.Default.Speed,
                    label = speedLabel(model.estimatedSpeed)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                                    text = stringResource(R.string.models_downloaded),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(onClick = onSelectClick) {
                                    Text(stringResource(R.string.models_use))
                                }
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
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.models_delete)) },
                                            onClick = {
                                                showMenu = false
                                                onDeleteClick()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        )
                                    }
                                }
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
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.models_download, formatModelSize(model.sizeInMB)))
                        }
                    }
                }
                DownloadStatus.DOWNLOADING -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.models_downloading,
                                    StorageHelper.formatStorageSize(downloadState.downloadedBytes / (1024 * 1024)),
                                    StorageHelper.formatStorageSize(downloadState.totalBytes / (1024 * 1024))
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.models_percent, (downloadState.progress * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onPauseClick) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = stringResource(R.string.models_pause)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { animatedProgress },
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
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.models_use))
                    }
                }
                DownloadStatus.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.models_download_failed, downloadState.error.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownloadClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.models_retry_download))
                        }
                    }
                }
                DownloadStatus.PAUSED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.models_paused, (downloadState.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = onResumeClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.models_resume))
                        }
                    }
                }
            }
        }
    }
}

/** Localized label for the catalog's speed values ("Fast", "Medium", "Slow"). */
@Composable
private fun speedLabel(speed: String): String = when (speed) {
    "Fast" -> stringResource(R.string.models_speed_fast)
    "Medium" -> stringResource(R.string.models_speed_medium)
    "Slow" -> stringResource(R.string.models_speed_slow)
    else -> speed
}

@Composable
fun ModelSpec(
    icon: ImageVector,
    label: String
) {
    val color = LocalContentColor.current.copy(alpha = 0.7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
