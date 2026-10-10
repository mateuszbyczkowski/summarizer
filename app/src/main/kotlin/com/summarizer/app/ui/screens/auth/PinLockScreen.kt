package com.summarizer.app.ui.screens.auth

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.util.BiometricHelper

/** Wrong entries left at which the remaining count is shown. */
private const val ATTEMPTS_WARNING_THRESHOLD = 3

/** The app PIN on the keypad, for phones without a screen lock. */
@Composable
fun PinLockScreen(
    onUnlocked: () -> Unit,
    viewModel: PinLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }
    // A fingerprint needs a screen lock on almost every phone, so this is rarely shown
    val canUseFingerprint = remember(context) { BiometricHelper.canUseBiometric(context) }
    var fingerprintError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }
    LaunchedEffect(state.rejections) {
        if (state.rejections > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            shake(shakeOffset)
        }
    }

    val attempts = state.attemptsBeforeLockout
    val message = when {
        state.lockoutSeconds > 0 -> stringResource(
            R.string.lock_pin_locked_out,
            DateUtils.formatElapsedTime(state.lockoutSeconds.toLong())
        )
        attempts != null && attempts <= ATTEMPTS_WARNING_THRESHOLD ->
            pluralStringResource(R.plurals.lock_pin_attempts_left, attempts, attempts)
        attempts != null -> stringResource(R.string.lock_pin_wrong)
        fingerprintError != null -> fingerprintError
        else -> null
    }

    PinEntryLayout(
        title = stringResource(R.string.lock_pin_title),
        subtitle = null,
        enteredCount = state.entry.length,
        message = message,
        isError = message != null,
        keypadEnabled = state.keypadEnabled,
        shakeOffset = shakeOffset,
        onDigit = {
            fingerprintError = null
            viewModel.onDigit(it)
        },
        onBackspace = viewModel::onBackspace,
        onFingerprint = if (canUseFingerprint) {
            {
                val activity = context.findFragmentActivity()
                if (activity != null) {
                    BiometricHelper.authenticate(
                        activity = activity,
                        title = resources.getString(R.string.lock_prompt_title, resources.getString(R.string.app_name)),
                        subtitle = null,
                        negativeButtonText = resources.getString(R.string.lock_fingerprint_use_pin),
                        onSuccess = onUnlocked,
                        onFailure = { failure ->
                            fingerprintError = (failure as? BiometricHelper.Failure.Error)?.message
                        }
                    )
                }
            }
        } else {
            null
        }
    )
}
