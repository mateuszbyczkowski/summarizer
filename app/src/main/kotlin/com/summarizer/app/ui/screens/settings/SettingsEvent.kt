package com.summarizer.app.ui.screens.settings

import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.OpenAIModel

/** User actions on the Settings screen. */
sealed interface SettingsEvent {
    data class SelectProvider(val provider: AIProvider) : SettingsEvent
    data class SaveApiKey(val apiKey: String) : SettingsEvent
    data object ValidateApiKey : SettingsEvent
    data object ClearApiKey : SettingsEvent
    data class SelectOpenAIModel(val model: OpenAIModel) : SettingsEvent
    data class SetAutoSummarizationEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAutoSummarizationHour(val hour: Int) : SettingsEvent
    data class SetDataRetentionDays(val days: Int) : SettingsEvent
    data class SetAppLockEnabled(val enabled: Boolean) : SettingsEvent

    /** The app PIN asked for when turning the app lock on was created, or the user backed out. */
    data object AppLockPinCreated : SettingsEvent
    data object AppLockPinCancelled : SettingsEvent
    data class SetSmartNotificationsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetSmartNotificationThreshold(val threshold: Float) : SettingsEvent

    /** Navigation: open the local model manager. Handled by the screen. */
    data object ManageModels : SettingsEvent

    /** Wipe all data and restart onboarding. Handled by the screen, which owns navigation. */
    data object ResetApp : SettingsEvent
}
