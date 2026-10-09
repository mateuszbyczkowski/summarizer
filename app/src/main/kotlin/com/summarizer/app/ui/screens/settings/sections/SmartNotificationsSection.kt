package com.summarizer.app.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.InfoCardTone
import com.summarizer.app.ui.components.SettingsSectionCard
import com.summarizer.app.ui.components.SwitchRow
import com.summarizer.app.ui.screens.settings.SettingsEvent

@Composable
fun SmartNotificationsSection(
    enabled: Boolean,
    threshold: Float,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_smart_notifications_title),
        description = stringResource(R.string.settings_smart_notifications_description),
        modifier = modifier
    ) {
        SwitchRow(
            title = stringResource(R.string.settings_smart_notifications_enable),
            checked = enabled,
            onCheckedChange = { onEvent(SettingsEvent.SetSmartNotificationsEnabled(it)) }
        )

        if (enabled) {
            // Dragging only updates local state; the value is persisted once the drag ends
            var sliderValue by remember(threshold) { mutableFloatStateOf(threshold) }

            Text(
                text = stringResource(
                    R.string.settings_smart_notifications_threshold,
                    stringResource(thresholdLabel(sliderValue))
                ),
                style = MaterialTheme.typography.labelLarge
            )

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    if (sliderValue != threshold) {
                        onEvent(SettingsEvent.SetSmartNotificationThreshold(sliderValue))
                    }
                },
                valueRange = 0.3f..0.9f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(thresholdDescription(sliderValue)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            InfoCard(
                text = stringResource(R.string.settings_smart_notifications_info),
                tone = InfoCardTone.Tip
            )
        }
    }
}

@StringRes
private fun thresholdLabel(threshold: Float): Int = when {
    threshold < 0.4f -> R.string.threshold_low
    threshold < 0.6f -> R.string.threshold_medium
    threshold < 0.8f -> R.string.threshold_high
    else -> R.string.threshold_very_high
}

@StringRes
private fun thresholdDescription(threshold: Float): Int = when {
    threshold < 0.4f -> R.string.threshold_low_description
    threshold < 0.6f -> R.string.threshold_medium_description
    threshold < 0.8f -> R.string.threshold_high_description
    else -> R.string.threshold_very_high_description
}
