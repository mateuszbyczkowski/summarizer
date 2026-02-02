package com.summarizer.app.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.summarizer.app.util.StorageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageLocationScreen(
    onLocationSelected: (StorageHelper.StorageLocation) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val internalStorage = remember { StorageHelper.getInternalStorageInfo(context) }
    val externalStorage = remember { StorageHelper.getExternalStorageInfo(context) }

    var selectedLocation by remember {
        mutableStateOf<StorageHelper.StorageLocation>(
            if (externalStorage != null && externalStorage.availableSpaceMB > internalStorage.availableSpaceMB) {
                StorageHelper.StorageLocation.EXTERNAL
            } else {
                StorageHelper.StorageLocation.INTERNAL
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Storage Location") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
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
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Where would you like to store AI models?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI models are large files (700MB - 1.8GB). Choose a storage location with sufficient space.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Internal Storage Option
                StorageOptionCard(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Internal Storage",
                    storageInfo = internalStorage,
                    isSelected = selectedLocation == StorageHelper.StorageLocation.INTERNAL,
                    onSelect = { selectedLocation = StorageHelper.StorageLocation.INTERNAL }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // External Storage Option (if available)
                if (externalStorage != null) {
                    StorageOptionCard(
                        icon = Icons.Default.SdCard,
                        title = "External Storage",
                        storageInfo = externalStorage,
                        isSelected = selectedLocation == StorageHelper.StorageLocation.EXTERNAL,
                        onSelect = { selectedLocation = StorageHelper.StorageLocation.EXTERNAL }
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdCard,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "External Storage (Not Available)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
                        info.availableSpaceMB < 700 -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = "⚠️ Insufficient storage space (${info.availableSpaceMB}MB available, 700MB minimum required). Please free up space or choose another location.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        info.availableSpaceMB < 2000 -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text(
                                    text = "ℹ️ Low storage space (${info.availableSpaceMB}MB available). You can continue, but consider freeing up space for larger models.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onLocationSelected(selectedLocation) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                // Always enabled - users can skip model download anyway
            ) {
                Text("Continue")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            .selectable(
                selected = isSelected,
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
                    text = "Available: ${StorageHelper.formatStorageSizeGB(storageInfo.availableSpaceGB)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Storage bar
                LinearProgressIndicator(
                    progress = (storageInfo.usedPercent / 100f).coerceIn(0f, 1f),
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
                    text = "${storageInfo.usedPercent}% used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
