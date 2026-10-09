package com.summarizer.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.ui.components.ApiKeyHowToCard
import com.summarizer.app.ui.components.ApiKeyTextField
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.OpenAIPrivacyNoticeCard
import com.summarizer.app.ui.screens.settings.SettingsUiState
import com.summarizer.app.ui.screens.settings.SettingsViewModel

/**
 * Onboarding screen for setting up OpenAI API key.
 */
@Composable
fun OpenAISetupScreen(
    onSetupComplete: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Deliberately not saveable: the key must not end up in the saved instance state
    var apiKeyInput by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = (uiState as? SettingsUiState.Error)?.message
    val canSave = apiKeyInput.isNotBlank() && errorMessage == null
    val saveKey = { if (canSave) viewModel.saveApiKey(apiKeyInput.trim()) }

    // Auto-navigate when API key is saved
    LaunchedEffect(uiState) {
        if ((uiState as? SettingsUiState.Success)?.hasApiKey == true) {
            onSetupComplete()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.openai_setup_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.openai_setup_heading),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.openai_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApiKeyTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            // Editing the key clears a previous save error so it can be retried
                            if (errorMessage != null) viewModel.dismissError()
                        },
                        isError = errorMessage != null,
                        onDone = saveKey,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                ApiKeyHowToCard()

                OpenAIPrivacyNoticeCard(
                    suggestion = stringResource(R.string.openai_privacy_suggestion_onboarding)
                )

                InfoCard(
                    title = stringResource(R.string.openai_setup_cost_title),
                    text = stringResource(R.string.openai_setup_cost_body),
                    tone = InfoCardTone.Neutral,
                    icon = Icons.Outlined.Payments
                )
            }

            // Continue button - fixed at the bottom
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = saveKey,
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.continue_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TextButton(
                    onClick = onSetupComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.openai_setup_skip))
                }
            }
        }
    }
}
