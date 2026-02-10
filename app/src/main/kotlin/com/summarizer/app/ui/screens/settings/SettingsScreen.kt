package com.summarizer.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.OpenAIModel
import com.summarizer.app.util.PermissionHelper

/**
 * Settings screen for configuring AI provider and OpenAI API key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    onModelConfigClick: () -> Unit = {},
    onResetComplete: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is SettingsUiState.Success -> {
                SettingsContent(
                    state = state,
                    onProviderSelected = viewModel::setAIProvider,
                    onApiKeySaved = viewModel::saveApiKey,
                    onApiKeyValidate = viewModel::validateApiKey,
                    onApiKeyCleared = viewModel::clearApiKey,
                    onModelConfigClick = onModelConfigClick,
                    onSelectedModelChanged = viewModel::setSelectedModel,
                    onAutoSummarizationEnabledChanged = viewModel::setAutoSummarizationEnabled,
                    onAutoSummarizationHourChanged = viewModel::setAutoSummarizationHour,
                    onDataRetentionDaysChanged = viewModel::setDataRetentionDays,
                    onBiometricEnabledChanged = viewModel::setBiometricEnabled,
                    onSmartNotificationsEnabledChanged = viewModel::setSmartNotificationsEnabled,
                    onSmartNotificationThresholdChanged = viewModel::setSmartNotificationThreshold,
                    onResetApp = {
                        viewModel.resetApplication()
                        onResetComplete()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is SettingsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::dismissError) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Success,
    onProviderSelected: (AIProvider) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onApiKeyValidate: () -> Unit,
    onApiKeyCleared: () -> Unit,
    onModelConfigClick: () -> Unit,
    onSelectedModelChanged: (OpenAIModel) -> Unit,
    onAutoSummarizationEnabledChanged: (Boolean) -> Unit,
    onAutoSummarizationHourChanged: (Int) -> Unit,
    onDataRetentionDaysChanged: (Int) -> Unit,
    onBiometricEnabledChanged: (Boolean) -> Unit,
    onSmartNotificationsEnabledChanged: (Boolean) -> Unit,
    onSmartNotificationThresholdChanged: (Float) -> Unit,
    onResetApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context))
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Smart Notifications Section (MOVED TO TOP - Most frequently used)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Smart Notifications",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "AI-powered filtering to only notify you of important messages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Enable/Disable Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable smart notifications",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = state.smartNotificationsEnabled,
                        onCheckedChange = onSmartNotificationsEnabledChanged
                    )
                }

                // Importance threshold slider (only shown if enabled)
                if (state.smartNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Importance threshold: ${getThresholdLabel(state.smartNotificationThreshold)}",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Slider(
                        value = state.smartNotificationThreshold,
                        onValueChange = onSmartNotificationThresholdChanged,
                        valueRange = 0.3f..0.9f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Threshold description
                    Text(
                        text = getThresholdDescription(state.smartNotificationThreshold),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "💡 Smart notifications use AI to analyze message content and only notify you when messages are likely important. Lower threshold = more notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Auto-Summarization Schedule
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Daily Auto-Summarization",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Automatically summarize followed threads at a scheduled time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Enable/Disable Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable auto-summarization",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = state.autoSummarizationEnabled,
                        onCheckedChange = onAutoSummarizationEnabledChanged
                    )
                }

                // Time picker (only shown if enabled)
                if (state.autoSummarizationEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Summary time",
                        style = MaterialTheme.typography.labelLarge
                    )

                    // Hour selector using dropdown
                    var showTimeMenu by remember { mutableStateOf(false) }
                    OutlinedCard(
                        onClick = { showTimeMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${String.format("%02d", state.autoSummarizationHour)}:00 (${formatHourTo12Hour(state.autoSummarizationHour)})",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                imageVector = if (showTimeMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Select time"
                            )
                        }
                    }

                    // Dropdown menu for hour selection
                    DropdownMenu(
                        expanded = showTimeMenu,
                        onDismissRequest = { showTimeMenu = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        (0..23).forEach { hour ->
                            DropdownMenuItem(
                                text = {
                                    Text("${String.format("%02d", hour)}:00 (${formatHourTo12Hour(hour)})")
                                },
                                onClick = {
                                    onAutoSummarizationHourChanged(hour)
                                    showTimeMenu = false
                                }
                            )
                        }
                    }

                    // Info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "ℹ️ Only followed threads will be summarized. Use the star icon on threads to follow them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Data Retention Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Data Retention",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Automatically delete old messages and summaries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Retention period selector
                var showRetentionMenu by remember { mutableStateOf(false) }
                Text(
                    text = "Keep data for",
                    style = MaterialTheme.typography.labelLarge
                )

                OutlinedCard(
                    onClick = { showRetentionMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.dataRetentionDays} days",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = if (showRetentionMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Select retention period"
                        )
                    }
                }

                // Dropdown menu for retention selection
                DropdownMenu(
                    expanded = showRetentionMenu,
                    onDismissRequest = { showRetentionMenu = false }
                ) {
                    listOf(7, 14, 30, 60, 90, 180, 365).forEach { days ->
                        DropdownMenuItem(
                            text = { Text("$days days") },
                            onClick = {
                                onDataRetentionDaysChanged(days)
                                showRetentionMenu = false
                            }
                        )
                    }
                }

                // Info card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "ℹ️ Data older than the retention period will be automatically deleted daily. At least 30 recent messages per thread are always kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Biometric Authentication Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Unlock the app using biometric authentication",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val biometricAvailable = com.summarizer.app.util.BiometricHelper.canUseBiometric(context)

                // Enable/Disable Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable biometric unlock",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = state.biometricEnabled,
                        onCheckedChange = onBiometricEnabledChanged,
                        enabled = biometricAvailable
                    )
                }

                // Status message
                if (!biometricAvailable) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "⚠️ ${com.summarizer.app.util.BiometricHelper.getBiometricStatusMessage(context)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (state.biometricEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "✓ Biometric unlock is enabled. You can use fingerprint or face recognition to unlock the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Permissions Status Card (MOVED DOWN - One-time setup)
        PermissionsStatusCard(
            hasNotificationPermission = hasNotificationPermission,
            onRefreshPermissions = {
                hasNotificationPermission = PermissionHelper.hasNotificationListenerPermission(context)
            }
        )

        // AI Provider Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI Provider",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Choose how WhatsApp messages are summarized",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Local Provider Option
                ProviderOption(
                    title = "Local (On-device)",
                    description = "Privacy-first • Offline • Free • Requires storage",
                    icon = Icons.Default.PhoneAndroid,
                    isSelected = state.aiProvider == AIProvider.LOCAL,
                    onClick = { onProviderSelected(AIProvider.LOCAL) }
                )

                // OpenAI Provider Option
                ProviderOption(
                    title = "OpenAI (Cloud)",
                    description = "Fast • High-quality • Requires API key • ~$0.0006 per summary",
                    icon = Icons.Default.Cloud,
                    isSelected = state.aiProvider == AIProvider.OPENAI,
                    onClick = { onProviderSelected(AIProvider.OPENAI) }
                )
            }
        }

        // Local Model Configuration (only shown if Local selected)
        if (state.aiProvider == AIProvider.LOCAL) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Local Model Configuration",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Download and manage AI models for on-device processing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onModelConfigClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Models")
                    }

                    // Info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "💡 Local models provide complete privacy - all processing happens on your device. No internet connection required after download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // OpenAI API Key Configuration (only shown if OpenAI selected)
        if (state.aiProvider == AIProvider.OPENAI) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "OpenAI Configuration",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (state.hasApiKey) {
                        // Show masked existing key
                        OutlinedTextField(
                            value = state.apiKeyMasked,
                            onValueChange = {},
                            label = { Text("API Key (configured)") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = onApiKeyCleared) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear API key")
                                }
                            }
                        )

                        // Validate button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onApiKeyValidate,
                                enabled = !state.isValidating,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isValidating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Validate API Key")
                            }
                        }

                        // Validation message
                        state.validationMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (message.startsWith("✓")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    } else {
                        // Show input for new key
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("OpenAI API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showApiKey) "Hide" else "Show"
                                    )
                                }
                            },
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )

                        Button(
                            onClick = { onApiKeySaved(apiKeyInput) },
                            enabled = apiKeyInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save API Key")
                        }

                        // Privacy notice
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ Privacy Notice: OpenAI provider sends your WhatsApp messages to OpenAI servers for processing. " +
                                        "Use the Local provider for complete privacy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // How to get API key
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "How to get an API key:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "1. Visit platform.openai.com\n" +
                                            "2. Sign up or log in\n" +
                                            "3. Go to API Keys section\n" +
                                            "4. Create new secret key\n" +
                                            "5. Copy and paste here",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // OpenAI Model Selection (only shown if OpenAI selected)
        if (state.aiProvider == AIProvider.OPENAI) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Model Selection",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Choose which OpenAI model to use for summaries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Model options with radio buttons
                    OpenAIModel.values().forEach { model ->
                        ModelOption(
                            model = model,
                            isSelected = state.selectedModel == model,
                            onClick = { onSelectedModelChanged(model) }
                        )
                    }

                    // Info card with recommendation
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "💡 Recommended: GPT-4o Mini offers the best balance of quality and cost for most users.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Cost Information (if OpenAI selected)
        if (state.aiProvider == AIProvider.OPENAI) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💰 Cost Estimation",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Using ${state.selectedModel.displayName}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• ${state.selectedModel.formatPricing()}\n" +
                                "• Input: $${String.format("%.2f", state.selectedModel.inputPricePer1MTokens)} per 1M tokens\n" +
                                "• Output: $${String.format("%.2f", state.selectedModel.outputPricePer1MTokens)} per 1M tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // About Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "WhatsApp Thread Summarizer",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Version 1.0.0 (I2)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Features:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "• On-device AI summarization\n" +
                            "• OpenAI integration\n" +
                            "• Thread following\n" +
                            "• Daily auto-summarization\n" +
                            "• End-to-end encryption",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Reset Application Section (Expandable)
        var isDangerZoneExpanded by remember { mutableStateOf(false) }
        var showResetDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header - Always visible
                OutlinedCard(
                    onClick = { isDangerZoneExpanded = !isDangerZoneExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Danger Zone",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Advanced reset options",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isDangerZoneExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDangerZoneExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Expandable Content
                if (isDangerZoneExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            text = "Reset application to factory defaults",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        // Warning text
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⚠️ This will:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "• Delete all downloaded AI models\n" +
                                            "• Remove all captured messages\n" +
                                            "• Clear all generated summaries\n" +
                                            "• Reset all settings to defaults\n" +
                                            "• Restart the onboarding process",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder().copy(
                                width = 1.dp,
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Application")
                        }
                    }
                }
            }
        }

        // Confirmation Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text("Reset Application?")
                },
                text = {
                    Text(
                        "This action cannot be undone. All your data, including downloaded models, messages, and summaries will be permanently deleted.\n\nAre you sure you want to continue?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            onResetApp()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionsStatusCard(
    hasNotificationPermission: Boolean,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    val isRestrictiveROM = PermissionHelper.isRestrictiveROM()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasNotificationPermission) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (hasNotificationPermission) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = if (hasNotificationPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (hasNotificationPermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }

            // Status message
            Text(
                text = if (hasNotificationPermission) {
                    "All required permissions are granted"
                } else {
                    "Notification access is required for the app to work"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasNotificationPermission) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )

            // Notification Permission Button
            OutlinedButton(
                onClick = {
                    PermissionHelper.openNotificationListenerSettings(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (hasNotificationPermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (hasNotificationPermission) {
                        "Manage Notification Access"
                    } else {
                        "Grant Notification Access"
                    }
                )
            }

            // AutoStart Button (only for restrictive ROMs)
            if (isRestrictiveROM) {
                OutlinedButton(
                    onClick = {
                        PermissionHelper.openAutoStartSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasNotificationPermission) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage ${PermissionHelper.getROMType()} AutoStart")
                }

                // Info for restrictive ROM
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "ℹ️ ${PermissionHelper.getROMType()} requires AutoStart permission for background functionality",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasNotificationPermission) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Refresh button
            TextButton(
                onClick = onRefreshPermissions,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Refresh Status",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ProviderOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(width = 2.dp)
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ModelOption(
    model: OpenAIModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(width = 2.dp)
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = model.formatPricing(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Helper function to format hour (0-23) to 12-hour format
 */
private fun formatHourTo12Hour(hour: Int): String {
    return when {
        hour == 0 -> "12:00 AM"
        hour < 12 -> "$hour:00 AM"
        hour == 12 -> "12:00 PM"
        else -> "${hour - 12}:00 PM"
    }
}

/**
 * Helper function to get threshold label
 */
private fun getThresholdLabel(threshold: Float): String {
    return when {
        threshold < 0.4f -> "Low (more notifications)"
        threshold < 0.6f -> "Medium"
        threshold < 0.8f -> "High (fewer notifications)"
        else -> "Very High (only critical)"
    }
}

/**
 * Helper function to get threshold description
 */
private fun getThresholdDescription(threshold: Float): String {
    return when {
        threshold < 0.4f -> "You'll receive notifications for most messages, including casual conversation."
        threshold < 0.6f -> "You'll receive notifications for questions, requests, and important updates."
        threshold < 0.8f -> "You'll receive notifications only for urgent matters and direct questions."
        else -> "You'll receive notifications only for critical and time-sensitive messages."
    }
}
