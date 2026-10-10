package com.summarizer.app.ui.screens.settings.sections

import androidx.compose.foundation.layout.fillMaxSize
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
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.SettingsSectionCard
import com.summarizer.app.ui.components.SwitchRow
import com.summarizer.app.ui.screens.auth.PinSetupScreen
import com.summarizer.app.ui.screens.settings.SettingsEvent
import com.summarizer.app.util.BiometricHelper
import java.util.UUID

@Composable
fun SecuritySection(
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

    SettingsSectionCard(
        title = stringResource(R.string.settings_security_title),
        description = stringResource(R.string.lock_settings_description),
        modifier = modifier
    ) {
        SwitchRow(
            title = stringResource(R.string.lock_settings_app_lock),
            checked = appLockEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetAppLockEnabled(it)) }
        )

        when {
            !appLockEnabled -> InfoCard(
                text = stringResource(R.string.lock_settings_off_info),
                tone = InfoCardTone.Warning
            )
            deviceSecure -> InfoCard(text = stringResource(R.string.lock_settings_system_info))
            else -> InfoCard(text = stringResource(R.string.lock_settings_pin_info))
        }
    }

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
