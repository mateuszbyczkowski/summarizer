package com.summarizer.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.screens.settings.sections.AIProviderSection
import com.summarizer.app.ui.screens.settings.sections.AboutSection
import com.summarizer.app.ui.screens.settings.sections.AutoSummarizationSection
import com.summarizer.app.ui.screens.settings.sections.DangerZoneSection
import com.summarizer.app.ui.screens.settings.sections.DataRetentionSection
import com.summarizer.app.ui.screens.settings.sections.LocalModelSection
import com.summarizer.app.ui.screens.settings.sections.OpenAIApiKeySection
import com.summarizer.app.ui.screens.settings.sections.OpenAIModelSection
import com.summarizer.app.ui.screens.settings.sections.PermissionsStatusSection
import com.summarizer.app.ui.screens.settings.sections.ResettingDialog
import com.summarizer.app.ui.screens.settings.sections.SecuritySection
import com.summarizer.app.ui.screens.settings.sections.SmartNotificationsSection

/**
 * Settings screen: AI provider, notifications, scheduling, retention, security and reset.
 */
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    onModelConfigClick: () -> Unit = {},
    onResetComplete: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isResetting by viewModel.isResetting.collectAsStateWithLifecycle()
    val currentOnModelConfigClick by rememberUpdatedState(onModelConfigClick)
    val currentOnResetComplete by rememberUpdatedState(onResetComplete)

    val onEvent: (SettingsEvent) -> Unit = remember(viewModel) {
        { event ->
            when (event) {
                SettingsEvent.ManageModels -> currentOnModelConfigClick()
                SettingsEvent.ResetApp -> viewModel.resetApplication(onComplete = { currentOnResetComplete() })
                else -> viewModel.onEvent(event)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                onBackClick = onBackPressed
            )
        }
    ) { paddingValues ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)

        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is SettingsUiState.Success -> {
                SettingsContent(
                    state = state,
                    isResetting = isResetting,
                    onEvent = onEvent,
                    modifier = contentModifier
                )
            }

            is SettingsUiState.Error -> {
                Box(
                    modifier = contentModifier.padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = viewModel::dismissError) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }

    if (isResetting) {
        ResettingDialog()
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Success,
    isResetting: Boolean,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Most frequently used settings first; one-time setup further down
        SmartNotificationsSection(
            enabled = state.smartNotificationsEnabled,
            threshold = state.smartNotificationThreshold,
            onEvent = onEvent
        )

        AutoSummarizationSection(
            enabled = state.autoSummarizationEnabled,
            hour = state.autoSummarizationHour,
            onEvent = onEvent
        )

        DataRetentionSection(
            retentionDays = state.dataRetentionDays,
            onEvent = onEvent
        )

        SecuritySection(
            biometricEnabled = state.biometricEnabled,
            onEvent = onEvent
        )

        PermissionsStatusSection()

        AIProviderSection(
            provider = state.aiProvider,
            onEvent = onEvent
        )

        when (state.aiProvider) {
            AIProvider.LOCAL -> LocalModelSection(onEvent = onEvent)
            AIProvider.OPENAI -> {
                OpenAIApiKeySection(
                    hasApiKey = state.hasApiKey,
                    apiKeyMasked = state.apiKeyMasked,
                    isValidating = state.isValidating,
                    validationMessage = state.validationMessage,
                    onEvent = onEvent
                )
                OpenAIModelSection(
                    selectedModel = state.selectedModel,
                    onEvent = onEvent
                )
            }
        }

        AboutSection()

        DangerZoneSection(
            isResetting = isResetting,
            onEvent = onEvent
        )
    }
}
