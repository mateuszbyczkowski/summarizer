package com.summarizer.app.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.formatModelSize
import com.summarizer.app.util.StorageHelper

private const val MIN_REQUIRED_SPACE_MB = 700L
private const val LOW_SPACE_MB = 2000L

@Composable
fun StorageLocationScreen(
    onLocationSelected: (StorageHelper.StorageLocation) -> Unit,
    onBackClick: (() -> Unit)? = null,
    viewModel: StorageLocationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val internalStorage = remember { StorageHelper.getInternalStorageInfo(context) }
    val externalStorage = remember { StorageHelper.getExternalStorageInfo(context) }
    val savedLocation by viewModel.savedLocation.collectAsStateWithLifecycle()

    var selectedLocation by rememberSaveable(savedLocation) {
        mutableStateOf(
            savedLocation ?: if (externalStorage != null && externalStorage.availableSpaceMB > internalStorage.availableSpaceMB) {
                StorageHelper.StorageLocation.EXTERNAL
            } else {
                StorageHelper.StorageLocation.INTERNAL
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.storage_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.storage_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.storage_subtitle,
                        formatModelSize(ModelDownloadViewModel.DEFAULT_MODELS.minOf { it.sizeInMB }),
                        formatModelSize(ModelDownloadViewModel.DEFAULT_MODELS.maxOf { it.sizeInMB })
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Internal Storage Option
                StorageOptionCard(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.storage_internal),
                    storageInfo = internalStorage,
                    isSelected = selectedLocation == StorageHelper.StorageLocation.INTERNAL,
                    onSelect = { selectedLocation = StorageHelper.StorageLocation.INTERNAL }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // External Storage Option (if available)
                if (externalStorage != null) {
                    StorageOptionCard(
                        icon = Icons.Default.SdCard,
                        title = stringResource(R.string.storage_external),
                        storageInfo = externalStorage,
                        isSelected = selectedLocation == StorageHelper.StorageLocation.EXTERNAL,
                        onSelect = { selectedLocation = StorageHelper.StorageLocation.EXTERNAL }
                    )
                } else {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdCard,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.storage_external_unavailable),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Warning if low space
                val selectedStorageInfo = if (selectedLocation == StorageHelper.StorageLocation.INTERNAL) {
                    internalStorage
                } else {
                    externalStorage
                }

                selectedStorageInfo?.let { info ->
                    when {
                        info.availableSpaceMB < MIN_REQUIRED_SPACE_MB -> {
                            InfoCard(
                                text = stringResource(
                                    R.string.storage_insufficient,
                                    info.availableSpaceMB,
                                    MIN_REQUIRED_SPACE_MB
                                ),
                                tone = InfoCardTone.Warning
                            )
                        }
                        info.availableSpaceMB < LOW_SPACE_MB -> {
                            InfoCard(
                                text = stringResource(R.string.storage_low, info.availableSpaceMB)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.saveLocation(selectedLocation) { onLocationSelected(selectedLocation) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                // Always enabled - users can skip model download anyway
            ) {
                Text(stringResource(R.string.continue_button))
            }
        }
    }
}

@Composable
fun StorageOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    storageInfo: StorageHelper.StorageInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.storage_available,
                        StorageHelper.formatStorageSizeGB(storageInfo.availableSpaceGB)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Storage bar
                LinearProgressIndicator(
                    progress = { (storageInfo.usedPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (storageInfo.usedPercent > 90) {
                        MaterialTheme.colorScheme.error
                    } else if (storageInfo.usedPercent > 75) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.storage_used_percent, storageInfo.usedPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
