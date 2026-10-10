package com.summarizer.app.ui.screens.settings

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.summarizer.app.BuildConfig
import com.summarizer.app.R

/** Which settings dialog is open; saveable so it survives rotation. */
enum class SettingsDialog { DailyTime, Sensitivity, Retention, About, Reset }

internal val RETENTION_OPTIONS = listOf(7, 14, 30, 60, 90, 180, 365)

/**
 * Picks the daily summary time. Auto-summarization runs at the top of an hour (see
 * WorkScheduler), so minutes are dropped and the dialog says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTimeDialog(
    hour: Int,
    onConfirm: (hour: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = 0,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_daily_time_title)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour) }) {
                Text(stringResource(R.string.settings_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        TimePicker(state = state)
        Text(
            text = stringResource(R.string.settings_daily_time_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/** Smart-notification sensitivity: the importance threshold as a slider with a plain explanation. */
@Composable
fun SensitivityDialog(
    threshold: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable { mutableFloatStateOf(threshold) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sensitivity_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(sensitivityLabel(value)),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.3f..0.9f,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(thresholdDescription(value)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_smart_notifications_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.settings_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Short name of a smart-notification threshold, as shown in the settings row and dialog. */
@StringRes
fun sensitivityLabel(threshold: Float): Int = when {
    threshold < 0.4f -> R.string.settings_sensitivity_low
    threshold < 0.6f -> R.string.settings_sensitivity_medium
    threshold < 0.8f -> R.string.settings_sensitivity_high
    else -> R.string.settings_sensitivity_very_high
}

@StringRes
private fun thresholdDescription(threshold: Float): Int = when {
    threshold < 0.4f -> R.string.threshold_low_description
    threshold < 0.6f -> R.string.threshold_medium_description
    threshold < 0.8f -> R.string.threshold_high_description
    else -> R.string.threshold_very_high_description
}

/** Message retention as a single-choice list; selecting an option saves it. */
@Composable
fun RetentionDialog(
    retentionDays: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(retentionDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_retention_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.selectableGroup()) {
                    RETENTION_OPTIONS.forEach { days ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = days == selected,
                                    role = Role.RadioButton,
                                    onClick = { selected = days }
                                ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = days == selected, onClick = null)
                            Text(
                                text = pluralStringResource(R.plurals.days, days, days),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_retention_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) { Text(stringResource(R.string.settings_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_about_app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_about_features_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.settings_about_features),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_dialog_close)) }
        }
    )
}

/** Confirms the reset and lists everything it deletes. */
@Composable
fun ResetConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_reset_consequences_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_reset_consequences),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_reset_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.settings_reset_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Blocking progress dialog shown while the app data is being wiped. */
@Composable
fun ResettingDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.settings_resetting_title)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.settings_resetting_message))
            }
        },
        confirmButton = {}
    )
}
