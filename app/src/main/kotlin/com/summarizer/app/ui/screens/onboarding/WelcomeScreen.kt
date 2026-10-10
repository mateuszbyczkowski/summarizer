package com.summarizer.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.summarizer.app.R

/** Step 1: shows what the app makes (a busy chat turned into three things) before asking for anything. */
@Composable
fun WelcomeScreen(
    onContinueClick: () -> Unit
) {
    OnboardingStepLayout(
        step = OnboardingStep.WELCOME,
        primaryLabel = stringResource(R.string.onboarding_welcome_start),
        onPrimary = onContinueClick,
        footer = { OnboardingHint(stringResource(R.string.onboarding_welcome_hint)) }
    ) {
        ExampleHero()
        OnboardingTitle(stringResource(R.string.onboarding_welcome_title))
        OnboardingLead(stringResource(R.string.onboarding_welcome_body))
        OnboardingNote(
            icon = Icons.Outlined.Shield,
            text = stringResource(R.string.onboarding_welcome_privacy),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** Fading chat bubbles behind a summary card: the app's output in one picture. */
@Composable
private fun ExampleHero() {
    val description = stringResource(R.string.onboarding_example_cd)
    OnboardingHero(
        modifier = Modifier
            .height(268.dp)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ChatBubble(R.string.onboarding_example_sender_1, R.string.onboarding_example_message_1, 18.dp, 12.dp, 0.55f, -2f)
            ChatBubble(R.string.onboarding_example_sender_2, R.string.onboarding_example_message_2, 46.dp, 54.dp, 0.7f, 1.5f)
            ChatBubble(R.string.onboarding_example_sender_3, R.string.onboarding_example_message_3, 24.dp, 94.dp, 0.85f, -1f)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(
                    text = stringResource(R.string.onboarding_example_more),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        SummaryCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 22.dp, end = 22.dp, bottom = 18.dp)
        )
    }
}

@Composable
private fun ChatBubble(sender: Int, message: Int, x: Dp, y: Dp, opacity: Float, degrees: Float) {
    Surface(
        modifier = Modifier
            .offset(x = x, y = y)
            .widthIn(max = 220.dp)
            .rotate(degrees)
            .alpha(opacity),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
        color = heroCardColor().copy(alpha = 0.8f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(sender),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = heroCardColor(),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.onboarding_example_chat),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                Text(
                    text = stringResource(R.string.onboarding_example_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SummaryLine(Icons.Outlined.TaskAlt, stringResource(R.string.onboarding_example_task))
            SummaryLine(Icons.Outlined.Event, stringResource(R.string.onboarding_example_trip))
            SummaryLine(Icons.Outlined.Event, stringResource(R.string.onboarding_example_meeting))
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
