package com.summarizer.app.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.summarizer.app.R
import com.summarizer.app.ui.components.SettingsRow
import com.summarizer.app.ui.components.SettingsRowTrailing
import com.summarizer.app.ui.screens.auth.PinSetupScreen
import com.summarizer.app.util.BiometricHelper
import java.util.UUID

/**
 * App lock on/off. Uses the phone's screen lock when it has one; otherwise turning the lock on
 * first asks for an app PIN (full-screen [PinSetupScreen] dialog).
 */
@Composable
fun AppLockRow(
    appLockEnabled: Boolean,
    showPinSetup: Boolean,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Re-checked on resume: the user may have changed the screen lock in system settings
    var deviceSecure by remember { mutableStateOf(BiometricHelper.isDeviceSecure(context)) }
    LifecycleResumeEffect(context) {
        deviceSecure = BiometricHelper.isDeviceSecure(context)
        onPauseOrDispose { }
    }

    SettingsRow(
        title = stringResource(R.string.lock_settings_app_lock),
        icon = when {
            !appLockEnabled -> Icons.Default.LockOpen
            deviceSecure -> Icons.Default.Fingerprint
            else -> Icons.Default.Pin
        },
        value = stringResource(
            when {
                !appLockEnabled -> R.string.settings_app_lock_off
                deviceSecure -> R.string.settings_app_lock_system
                else -> R.string.settings_app_lock_pin
            }
        ),
        trailing = SettingsRowTrailing.Switch(
            checked = appLockEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetAppLockEnabled(it)) }
        ),
        modifier = modifier
    )

    if (showPinSetup) {
        val session = rememberSaveable { UUID.randomUUID().toString() }
        val cancel = { onEvent(SettingsEvent.AppLockPinCancelled) }
        Dialog(
            onDismissRequest = cancel,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PinSetupScreen(
                    onPinSet = { onEvent(SettingsEvent.AppLockPinCreated) },
                    onCancel = cancel,
                    viewModel = hiltViewModel(key = "app-lock-pin-setup-$session")
                )
            }
        }
    }
}
