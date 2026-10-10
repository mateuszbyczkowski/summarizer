package com.summarizer.app.ui.screens.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.OpenAIModel
import com.summarizer.app.ui.components.ApiKeyHowToCard
import com.summarizer.app.ui.components.ApiKeyTextField
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.OpenAIPrivacyNoticeCard
import com.summarizer.app.ui.components.SettingsGroup
import com.summarizer.app.ui.components.SettingsRowIcon
import com.summarizer.app.ui.components.settingsGroupItem
import com.summarizer.app.ui.screens.settings.SettingsEvent
import com.summarizer.app.ui.screens.settings.SettingsUiState

/**
 * The OpenAI side of the AI engine screen: whether OpenAI is in use, the API key (add, check,
 * remove) and the model with its pricing. Driven by the Settings view model.
 */
@Composable
fun OpenAIEngineTab(
    state: SettingsUiState.Success,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = state.aiProvider == AIProvider.OPENAI && state.hasApiKey
    Column(modifier = modifier.fillMaxWidth()) {
        OpenAIStatusCard(isActive = isActive)

        SettingsGroup(label = stringResource(R.string.engine_openai_key_group)) {
            Column(
                modifier = Modifier
                    .settingsGroupItem()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.hasApiKey) {
                    SavedKey(state = state, isActive = isActive, onEvent = onEvent)
                } else {
                    NewKey(onEvent = onEvent)
                }
            }
        }

        SettingsGroup(label = stringResource(R.string.engine_openai_model_group)) {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OpenAIModel.entries.forEach { model ->
                    ModelOption(
                        model = model,
                        isSelected = state.selectedModel == model,
                        onClick = { onEvent(SettingsEvent.SelectOpenAIModel(model)) }
                    )
                }
            }
        }

        InfoCard(
            title = stringResource(R.string.settings_openai_cost_title),
            text = stringResource(
                R.string.settings_openai_cost_body,
                state.selectedModel.displayName,
                state.selectedModel.formatPricing(),
                state.selectedModel.inputPricePer1MTokens,
                state.selectedModel.outputPricePer1MTokens
            ),
            tone = InfoCardTone.Neutral,
            icon = Icons.Outlined.Payments,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun OpenAIStatusCard(isActive: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isActive) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (isActive) colors.onPrimaryContainer else colors.onSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowIcon(icon = if (isActive) Icons.Default.Check else Icons.Default.Cloud)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isActive) R.string.engine_openai_active_title else R.string.engine_openai_inactive_title
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.engine_openai_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedKey(
    state: SettingsUiState.Success,
    isActive: Boolean,
    onEvent: (SettingsEvent) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon = Icons.Default.Key)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.engine_openai_key_saved),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = state.apiKeyMasked,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isActive) {
            Button(onClick = { onEvent(SettingsEvent.SelectProvider(AIProvider.OPENAI)) }) {
                Text(stringResource(R.string.engine_openai_use))
            }
        }
        OutlinedButton(
            onClick = { onEvent(SettingsEvent.ValidateApiKey) },
            enabled = !state.isValidating
        ) {
            if (state.isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(stringResource(R.string.engine_openai_validate))
        }
        TextButton(
            onClick = { onEvent(SettingsEvent.ClearApiKey) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.engine_openai_remove))
        }
    }

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
}

@Composable
private fun NewKey(onEvent: (SettingsEvent) -> Unit) {
    // Deliberately not saveable: the key must not end up in the saved instance state
    var apiKeyInput by remember { mutableStateOf("") }
    val saveKey = {
        if (apiKeyInput.isNotBlank()) onEvent(SettingsEvent.SaveApiKey(apiKeyInput, activate = true))
    }

    ApiKeyTextField(
        value = apiKeyInput,
        onValueChange = { apiKeyInput = it },
        onDone = saveKey,
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = saveKey,
        enabled = apiKeyInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.engine_openai_save_use))
    }

    OpenAIPrivacyNoticeCard(
        suggestion = stringResource(R.string.openai_privacy_suggestion_settings)
    )

    ApiKeyHowToCard()
}

@Composable
private fun ModelOption(
    model: OpenAIModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .settingsGroupItem()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = model.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = model.formatPricing(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
