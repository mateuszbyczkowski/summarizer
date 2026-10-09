package com.summarizer.app.ui.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R
import com.summarizer.app.ui.components.DropdownSelector
import com.summarizer.app.ui.components.InfoCard
import com.summarizer.app.ui.components.SettingsSectionCard
import com.summarizer.app.ui.screens.settings.SettingsEvent

private val RETENTION_OPTIONS = listOf(7, 14, 30, 60, 90, 180, 365)

@Composable
fun DataRetentionSection(
    retentionDays: Int,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_retention_title),
        description = stringResource(R.string.settings_retention_description),
        modifier = modifier
    ) {
        DropdownSelector(
            label = stringResource(R.string.settings_retention_keep_for),
            selectedText = pluralStringResource(R.plurals.days, retentionDays, retentionDays),
            options = RETENTION_OPTIONS,
            optionText = { pluralStringResource(R.plurals.days, it, it) },
            onOptionSelected = { onEvent(SettingsEvent.SetDataRetentionDays(it)) }
        )

        InfoCard(text = stringResource(R.string.settings_retention_info))
    }
}
