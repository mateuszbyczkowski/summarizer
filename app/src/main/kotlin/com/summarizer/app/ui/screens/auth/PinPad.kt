package com.summarizer.app.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.util.Constants

private val KeySize = 72.dp
private val KeySpacing = 20.dp

/** The app's launcher icon (adaptive icon layers), drawn as a circle. */
@Composable
fun AppIconBadge(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center
    ) {
        // The adaptive foreground is 108 units with a 72-unit safe zone
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f)
        )
    }
}

/**
 * Full-screen layout for entering a PIN on the on-screen keypad: header, dots, a message line
 * and the keypad. Scrolls when the screen is too short (landscape, large font).
 */
@Composable
fun PinEntryLayout(
    title: String,
    subtitle: String?,
    enteredCount: Int,
    message: String?,
    isError: Boolean,
    keypadEnabled: Boolean,
    shakeOffset: Animatable<Float, *>,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onFingerprint: (() -> Unit)?,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconBadge(size = 56.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(32.dp))
            PinDots(
                enteredCount = enteredCount,
                isError = isError,
                modifier = Modifier.offset { IntOffset(shakeOffset.value.dp.roundToPx(), 0) }
            )
            Spacer(Modifier.height(16.dp))
            // Fixed height so the keypad does not jump when a message appears
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center
            ) {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PinKeypad(
                enabled = keypadEnabled,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onFingerprint = onFingerprint,
                backspaceEnabled = enteredCount > 0
            )
            footer()
        }
    }
}

@Composable
fun PinDots(
    enteredCount: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
    length: Int = Constants.PIN_LENGTH
) {
    val description = pluralStringResource(R.plurals.lock_pin_progress, length, enteredCount, length)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(length) { index ->
            val filled = index < enteredCount
            val color by animateColorAsState(
                targetValue = when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                },
                label = "pinDot"
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = 1.5.dp,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

/** Digits 0-9, an optional fingerprint key and backspace. Never opens the system keyboard. */
@Composable
fun PinKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onFingerprint: (() -> Unit)?,
    backspaceEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
                row.forEach { digit ->
                    DigitKey(digit = digit, enabled = enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
            if (onFingerprint != null) {
                IconKey(
                    contentDescription = stringResource(R.string.lock_key_fingerprint),
                    enabled = enabled,
                    onClick = onFingerprint
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Spacer(Modifier.size(KeySize))
            }
            DigitKey(digit = '0', enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onDigit('0')
            }
            IconKey(
                contentDescription = stringResource(R.string.lock_key_delete),
                enabled = enabled && backspaceEnabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onBackspace()
                }
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(KeySize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun IconKey(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .size(KeySize)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

/** Horizontal shake for a wrong PIN. */
suspend fun shake(offset: Animatable<Float, *>) {
    repeat(3) {
        offset.animateTo(15f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh))
        offset.animateTo(-15f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh))
    }
    offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
}
