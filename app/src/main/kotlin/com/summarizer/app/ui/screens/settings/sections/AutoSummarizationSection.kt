package com.summarizer.app.ui.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.ui.components.DropdownSelector
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.SettingsSectionCard
import com.summarizer.app.ui.components.SwitchRow
import com.summarizer.app.ui.screens.settings.SettingsEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AutoSummarizationSection(
    enabled: Boolean,
    hour: Int,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_auto_summary_title),
        description = stringResource(R.string.settings_auto_summary_description),
        modifier = modifier
    ) {
        SwitchRow(
            title = stringResource(R.string.settings_auto_summary_enable),
            checked = enabled,
            onCheckedChange = { onEvent(SettingsEvent.SetAutoSummarizationEnabled(it)) }
        )

        if (enabled) {
            DropdownSelector(
                label = stringResource(R.string.settings_auto_summary_time),
                selectedText = formatHour(hour),
                options = (0..23).toList(),
                optionText = { formatHour(it) },
                onOptionSelected = { onEvent(SettingsEvent.SetAutoSummarizationHour(it)) }
            )

            InfoCard(text = stringResource(R.string.settings_auto_summary_info))
        }
    }
}

/** Formats a full hour as e.g. "20:00 (8:00 PM)". */
@Composable
private fun formatHour(hour: Int): String {
    val time = LocalTime.of(hour, 0)
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(
        R.string.settings_hour_format,
        time.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
        time.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    )
}
