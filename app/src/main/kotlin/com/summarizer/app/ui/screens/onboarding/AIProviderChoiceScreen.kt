package com.summarizer.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.ui.components.StatusBadge
import com.summarizer.app.ui.components.formatModelSize
import com.summarizer.app.ui.screens.models.ModelDownloadViewModel
import com.summarizer.app.ui.components.AppTopBar
import com.summarizer.app.ui.screens.settings.SettingsViewModel

/**
 * Onboarding screen for choosing between Local (on-device) or OpenAI (cloud) provider.
 */
@Composable
fun AIProviderChoiceScreen(
    onProviderSelected: (AIProvider) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.provider_choice_title))
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Text(
                text = stringResource(R.string.provider_choice_heading),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.provider_choice_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Local Provider Card
            ProviderCard(
                title = stringResource(R.string.provider_local_title),
                icon = Icons.Default.PhoneAndroid,
                benefits = listOf(
                    stringResource(R.string.provider_choice_local_benefit_privacy),
                    stringResource(R.string.provider_choice_local_benefit_offline),
                    stringResource(R.string.provider_choice_local_benefit_free),
                    stringResource(
                        R.string.provider_choice_local_benefit_storage,
                        formatModelSize(ModelDownloadViewModel.DEFAULT_MODELS.minOf { it.sizeInMB }),
                        formatModelSize(ModelDownloadViewModel.DEFAULT_MODELS.maxOf { it.sizeInMB })
                    )
                ),
                buttonText = stringResource(R.string.provider_choice_use_local),
                onClick = {
                    viewModel.setAIProvider(AIProvider.LOCAL)
                    onProviderSelected(AIProvider.LOCAL)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                recommended = true
            )

            // OpenAI Provider Card
            ProviderCard(
                title = stringResource(R.string.provider_openai_title),
                icon = Icons.Default.Cloud,
                benefits = listOf(
                    stringResource(R.string.provider_choice_openai_benefit_fast),
                    stringResource(R.string.provider_choice_openai_benefit_quality),
                    stringResource(R.string.provider_choice_openai_benefit_cost),
                    stringResource(R.string.provider_choice_openai_benefit_requirements),
                    stringResource(R.string.provider_choice_openai_benefit_privacy)
                ),
                buttonText = stringResource(R.string.provider_choice_use_openai),
                onClick = {
                    viewModel.setAIProvider(AIProvider.OPENAI)
                    onProviderSelected(AIProvider.OPENAI)
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                recommended = false
            )

        }
    }
}

@Composable
private fun ProviderCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    benefits: List<String>,
    buttonText: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    recommended: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recommended badge (if applicable)
            if (recommended) {
                StatusBadge(
                    text = stringResource(R.string.badge_recommended),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = Icons.Default.Star
                )
            }

            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Benefits list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                benefits.forEach { benefit ->
                    Text(
                        text = benefit,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Action button
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}
