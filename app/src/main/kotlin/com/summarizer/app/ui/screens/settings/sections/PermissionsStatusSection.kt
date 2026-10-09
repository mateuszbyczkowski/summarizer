package com.summarizer.app.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.summarizer.app.R
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.util.PermissionHelper

/**
 * Shows whether notification access is granted and links to the relevant system settings.
 * The status is re-checked whenever the screen resumes, e.g. after returning from system settings.
 */
@Composable
fun PermissionsStatusSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context))
    }
    LifecycleResumeEffect(context) {
        hasNotificationPermission = PermissionHelper.hasNotificationListenerPermission(context)
        onPauseOrDispose { }
    }
    val isRestrictiveROM = remember { PermissionHelper.isRestrictiveROM() }
    val romType = remember { PermissionHelper.getROMType() }

    val colors = MaterialTheme.colorScheme
    val (containerColor, contentColor) = if (hasNotificationPermission) {
        colors.primaryContainer to colors.onPrimaryContainer
    } else {
        colors.errorContainer to colors.onErrorContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (hasNotificationPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.settings_permissions_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = stringResource(
                    if (hasNotificationPermission) {
                        R.string.settings_permissions_granted
                    } else {
                        R.string.settings_permissions_missing
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = { PermissionHelper.openNotificationListenerSettings(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(
                        if (hasNotificationPermission) {
                            R.string.settings_permissions_manage_access
                        } else {
                            R.string.settings_permissions_grant_access
                        }
                    )
                )
            }

            if (isRestrictiveROM) {
                OutlinedButton(
                    onClick = { PermissionHelper.openAutoStartSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.settings_permissions_manage_autostart, romType))
                }

                InfoCard(text = stringResource(R.string.settings_permissions_autostart_info, romType))
            }
        }
    }
}
