package com.summarizer.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.summarizer.app.R
import com.summarizer.app.util.PermissionHelper

/**
 * Step 2: notification access. Moves on by itself when the user comes back from the system
 * settings with access turned on; "Not now" continues without it (Home keeps asking).
 */
@Composable
fun NotificationAccessScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context)) }
    // Set while the user is away in the system settings this step opened
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    // However the step is left, a later return (Back from step 3) must not move on by itself
    val leave = {
        openedSettings = false
        onContinue()
    }

    LifecycleResumeEffect(Unit) {
        granted = PermissionHelper.hasNotificationListenerPermission(context)
        if (OnboardingFlow.autoAdvance(openedSettings, granted)) leave()
        onPauseOrDispose { }
    }

    OnboardingStepLayout(
        step = OnboardingStep.NOTIFICATION_ACCESS,
        primaryLabel = stringResource(if (granted) R.string.onboarding_engine_continue else R.string.onboarding_access_open),
        onPrimary = {
            if (granted) {
                leave()
            } else {
                openedSettings = true
                PermissionHelper.openNotificationListenerSettings(context)
            }
        },
        footer = {
            if (granted) {
                OnboardingHint(stringResource(R.string.onboarding_access_granted))
            } else {
                OnboardingHint(stringResource(R.string.onboarding_access_hint))
                TextButton(onClick = leave) { Text(stringResource(R.string.onboarding_access_later)) }
            }
        }
    ) {
        AccessHero(granted = granted)
        OnboardingTitle(stringResource(R.string.onboarding_access_title))
        OnboardingLead(stringResource(R.string.onboarding_access_body))
        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnboardingNote(icon = Icons.Outlined.Forum, text = stringResource(R.string.onboarding_access_only_whatsapp))
            OnboardingNote(icon = Icons.Outlined.Lock, text = stringResource(R.string.onboarding_access_on_device))
        }
        if (PermissionHelper.isRestrictiveROM()) {
            RestrictiveRomCard(modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** A picture of the system setting the user is about to turn on. */
@Composable
private fun AccessHero(granted: Boolean) {
    OnboardingHero(
        modifier = Modifier
            .height(180.dp)
            .clearAndSetSemantics { }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = heroCardColor(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RoundIcon(Icons.Outlined.AutoAwesome)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_access_setting),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_access_setting_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Off until access is granted, like the real setting
                Switch(checked = granted, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Xiaomi, Oppo and similar phones also need AutoStart, or the listener is killed. */
@Composable
private fun RestrictiveRomCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.onboarding_access_rom_title, PermissionHelper.getROMType()),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.onboarding_access_rom_body),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = { PermissionHelper.openAutoStartSettings(context) }) {
                Text(stringResource(R.string.onboarding_access_rom_open))
            }
        }
    }
}
