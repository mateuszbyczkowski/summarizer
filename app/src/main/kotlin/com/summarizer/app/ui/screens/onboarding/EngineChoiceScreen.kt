package com.summarizer.app.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.ui.components.formatModelSize
import com.summarizer.app.ui.screens.models.ModelProfiles

/**
 * Step 3: where summaries are made. "On this phone" is preselected; confirming it starts the
 * download of a model picked for the phone's RAM and goes on without waiting for it. OpenAI
 * leads to the API key screen.
 */
@Composable
fun EngineChoiceScreen(
    onNext: (OnboardingNext) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var selected by rememberSaveable { mutableStateOf(AIProvider.LOCAL) }
    OnboardingNextEffect(viewModel, onNext)

    OnboardingStepLayout(
        step = OnboardingStep.ENGINE,
        primaryLabel = stringResource(
            if (selected == AIProvider.LOCAL) R.string.onboarding_engine_start else R.string.onboarding_engine_continue
        ),
        onPrimary = { viewModel.confirmEngine(selected) }
    ) {
        OnboardingTitle(stringResource(R.string.onboarding_engine_title))
        OnboardingLead(stringResource(R.string.onboarding_engine_body))
        Column(
            modifier = Modifier
                .padding(top = 24.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val model = viewModel.recommendedModel
            EngineOption(
                icon = Icons.Outlined.PhoneAndroid,
                title = stringResource(R.string.onboarding_engine_local_title),
                badge = stringResource(R.string.onboarding_engine_recommended),
                body = stringResource(R.string.onboarding_engine_local_body),
                detail = if (model != null) {
                    stringResource(
                        R.string.onboarding_engine_local_model,
                        ModelProfiles.technicalName(model),
                        formatModelSize(model.sizeInMB),
                        viewModel.deviceRamGb
                    )
                } else {
                    stringResource(R.string.onboarding_engine_local_no_model)
                },
                selected = selected == AIProvider.LOCAL,
                onSelect = { selected = AIProvider.LOCAL }
            )
            EngineOption(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.onboarding_engine_openai_title),
                badge = null,
                body = stringResource(R.string.onboarding_engine_openai_body),
                detail = null,
                selected = selected == AIProvider.OPENAI,
                onSelect = { selected = AIProvider.OPENAI }
            )
        }
    }
}

@Composable
private fun EngineOption(
    icon: ImageVector,
    title: String,
    badge: String?,
    body: String,
    detail: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = CircleShape, color = if (selected) colors.primary else colors.secondaryContainer) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) colors.onPrimary else colors.onSecondaryContainer,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) {
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.primary) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            // The whole card is the radio button for TalkBack
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** Hands [OnboardingViewModel.next] to [onNext] once, when it is decided. */
@Composable
fun OnboardingNextEffect(viewModel: OnboardingViewModel, onNext: (OnboardingNext) -> Unit) {
    val next by viewModel.next.collectAsStateWithLifecycle()
    LaunchedEffect(next) {
        next?.let {
            viewModel.onNavigated()
            onNext(it)
        }
    }
}
