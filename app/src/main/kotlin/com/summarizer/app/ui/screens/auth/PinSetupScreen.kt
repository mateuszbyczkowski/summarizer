package com.summarizer.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R

/**
 * Creates the app PIN on the keypad: enter, then confirm. Used in onboarding, on the lock
 * screen when the phone lost its screen lock, and from Settings (with [onCancel]).
 */
@Composable
fun PinSetupScreen(
    onPinSet: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = stringResource(R.string.lock_pin_setup_subtitle),
    onCancel: (() -> Unit)? = null,
    viewModel: PinSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flow = state.flow
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(state.saved) {
        if (state.saved) onPinSet()
    }
    LaunchedEffect(flow.mismatch) {
        if (flow.mismatch) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            shake(shakeOffset)
        }
    }
    BackHandler(enabled = flow.confirming, onBack = viewModel::restart)

    PinEntryLayout(
        title = stringResource(if (flow.confirming) R.string.lock_pin_setup_confirm_title else R.string.lock_pin_setup_title),
        subtitle = if (flow.confirming) stringResource(R.string.lock_pin_setup_confirm_subtitle) else subtitle,
        enteredCount = flow.entry.length,
        message = when {
            state.saveFailed -> stringResource(R.string.lock_pin_setup_save_failed)
            flow.mismatch -> stringResource(R.string.lock_pin_setup_mismatch)
            else -> null
        },
        isError = state.saveFailed || flow.mismatch,
        keypadEnabled = flow.completedPin == null,
        shakeOffset = shakeOffset,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onFingerprint = null,
        modifier = modifier,
        footer = {
            if (onCancel != null) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.lock_pin_setup_cancel)) }
            }
        }
    )
}
