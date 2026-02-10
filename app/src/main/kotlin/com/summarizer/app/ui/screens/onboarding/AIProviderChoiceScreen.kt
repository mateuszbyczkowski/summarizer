package com.summarizer.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.ui.screens.settings.SettingsViewModel

/**
 * Onboarding screen for choosing between Local (on-device) or OpenAI (cloud) provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIProviderChoiceScreen(
    onProviderSelected: (AIProvider) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose AI Provider") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
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
                text = "How would you like to generate summaries?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "You can change this later in Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Local Provider Card
            ProviderCard(
                title = "Local (On-device)",
                icon = Icons.Default.PhoneAndroid,
                benefits = listOf(
                    "🔒 Complete Privacy - Everything stays on your device",
                    "🌐 Works Offline - No internet required",
                    "💰 Completely Free - No API costs",
                    "📦 Requires 700MB-1.8GB storage for AI model"
                ),
                buttonText = "Use Local Model",
                onClick = {
                    viewModel.setAIProvider(AIProvider.LOCAL)
                    onProviderSelected(AIProvider.LOCAL)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                recommended = true
            )

            // OpenAI Provider Card
            ProviderCard(
                title = "OpenAI (Cloud)",
                icon = Icons.Default.Cloud,
                benefits = listOf(
                    "⚡ Faster Processing - Cloud-based",
                    "🎯 High Quality - State-of-the-art model",
                    "💵 Very Affordable - ~$0.0006 per summary",
                    "🌐 Requires Internet & API Key",
                    "⚠️ Messages sent to OpenAI servers"
                ),
                buttonText = "Use OpenAI",
                onClick = {
                    viewModel.setAIProvider(AIProvider.OPENAI)
                    onProviderSelected(AIProvider.OPENAI)
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                recommended = false
            )

            Spacer(modifier = Modifier.weight(1f))
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
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "RECOMMENDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
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
