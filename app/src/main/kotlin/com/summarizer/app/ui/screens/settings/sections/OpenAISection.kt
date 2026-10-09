package com.summarizer.app.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.domain.model.OpenAIModel
import com.summarizer.app.ui.components.ApiKeyHowToCard
import com.summarizer.app.ui.components.ApiKeyTextField
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.OpenAIPrivacyNoticeCard
import com.summarizer.app.ui.components.SettingsSectionCard
import com.summarizer.app.ui.screens.settings.SettingsEvent

@Composable
fun OpenAIApiKeySection(
    hasApiKey: Boolean,
    apiKeyMasked: String,
    isValidating: Boolean,
    validationMessage: String?,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_openai_title),
        modifier = modifier
    ) {
        if (hasApiKey) {
            OutlinedTextField(
                value = apiKeyMasked,
                onValueChange = {},
                label = { Text(stringResource(R.string.settings_openai_key_configured)) },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { onEvent(SettingsEvent.ClearApiKey) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.settings_openai_clear_key)
                        )
                    }
                }
            )

            Button(
                onClick = { onEvent(SettingsEvent.ValidateApiKey) },
                enabled = !isValidating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(R.string.settings_openai_validate_key))
            }

            validationMessage?.let { message ->
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
            // Deliberately not saveable: the key must not end up in the saved instance state
            var apiKeyInput by remember { mutableStateOf("") }
            val saveKey = { if (apiKeyInput.isNotBlank()) onEvent(SettingsEvent.SaveApiKey(apiKeyInput)) }

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
                Text(stringResource(R.string.settings_openai_save_key))
            }

            OpenAIPrivacyNoticeCard(
                suggestion = stringResource(R.string.openai_privacy_suggestion_settings)
            )

            ApiKeyHowToCard()
        }
    }
}

@Composable
fun OpenAIModelSection(
    selectedModel: OpenAIModel,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_openai_model_title),
        description = stringResource(R.string.settings_openai_model_description),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OpenAIModel.entries.forEach { model ->
                ModelOption(
                    model = model,
                    isSelected = selectedModel == model,
                    onClick = { onEvent(SettingsEvent.SelectOpenAIModel(model)) }
                )
            }
        }

        InfoCard(
            text = stringResource(R.string.settings_openai_model_recommendation),
            tone = InfoCardTone.Tip
        )

        InfoCard(
            title = stringResource(R.string.settings_openai_cost_title),
            text = stringResource(
                R.string.settings_openai_cost_body,
                selectedModel.displayName,
                selectedModel.formatPricing(),
                selectedModel.inputPricePer1MTokens,
                selectedModel.outputPricePer1MTokens
            ),
            tone = InfoCardTone.Neutral,
            icon = Icons.Outlined.Payments
        )
    }
}

@Composable
private fun ModelOption(
    model: OpenAIModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.outlinedShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
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
