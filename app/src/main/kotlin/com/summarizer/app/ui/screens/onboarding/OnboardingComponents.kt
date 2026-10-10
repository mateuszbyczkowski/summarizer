package com.summarizer.app.ui.screens.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.summarizer.app.R

/**
 * The frame of a numbered onboarding step: step dots, scrolling [content] and the primary
 * button pinned to the bottom, with an optional [footer] (hint or secondary action) below it.
 */
@Composable
fun OnboardingStepLayout(
    step: OnboardingStep,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            StepDots(step = step, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                content = content
            )
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = footer
            )
        }
    }
}

/** Dots for the steps, the current one drawn as a wider pill. Read as "Step 2 of 3". */
@Composable
fun StepDots(step: OnboardingStep, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step, step.number, OnboardingStep.COUNT)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OnboardingStep.entries.forEach { dot ->
            val current = dot == step
            val width by animateDpAsState(if (current) 24.dp else 8.dp, label = "stepDotWidth")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun OnboardingTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .padding(top = 22.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
fun OnboardingLead(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** A short reassurance with a round icon, on a tinted strip. */
@Composable
fun OnboardingNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Small centered text under the primary button. */
@Composable
fun OnboardingHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/** The tinted picture area at the top of a step. */
@Composable
fun OnboardingHero(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh)),
                RoundedCornerShape(28.dp)
            )
            .background(
                Brush.radialGradient(listOf(colors.tertiaryContainer, Color.Transparent), radius = 700f),
                RoundedCornerShape(28.dp)
            )
            .clip(RoundedCornerShape(28.dp)),
        content = content
    )
}

/**
 * Cards drawn on [OnboardingHero]: white in the light theme; in the dark theme a raised surface
 * (the lowest container would be black, a hole in the picture).
 */
@Composable
fun heroCardColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.surface.luminance() < 0.5f) colors.surfaceContainerHigh else colors.surfaceContainerLowest
}
