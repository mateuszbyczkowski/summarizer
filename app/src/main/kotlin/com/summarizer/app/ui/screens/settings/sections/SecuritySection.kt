package com.summarizer.app.ui.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.SettingsSectionCard
import com.summarizer.app.ui.components.SwitchRow
import com.summarizer.app.ui.screens.settings.SettingsEvent
import com.summarizer.app.util.BiometricHelper

@Composable
fun SecuritySection(
    biometricEnabled: Boolean,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val biometricAvailable = remember(context) { BiometricHelper.canUseBiometric(context) }

    SettingsSectionCard(
        title = stringResource(R.string.settings_security_title),
        description = stringResource(R.string.settings_security_description),
        modifier = modifier
    ) {
        SwitchRow(
            title = stringResource(R.string.settings_security_enable_biometric),
            checked = biometricEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetBiometricEnabled(it)) },
            enabled = biometricAvailable
        )

        if (!biometricAvailable) {
            InfoCard(
                text = remember(context) { BiometricHelper.getBiometricStatusMessage(context) },
                tone = InfoCardTone.Warning
            )
        } else if (biometricEnabled) {
            InfoCard(
                text = stringResource(R.string.settings_security_biometric_enabled_info),
                tone = InfoCardTone.Success
            )
        }
    }
}
