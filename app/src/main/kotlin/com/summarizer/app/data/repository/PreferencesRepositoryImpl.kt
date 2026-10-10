package com.summarizer.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.summarizer.app.data.local.preferences.SecurePreferences
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) : PreferencesRepository {

    private object PreferencesKeys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val STORAGE_LOCATION = stringPreferencesKey("storage_location")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
        val SETUP_MODEL_ID = stringPreferencesKey("setup_model_id")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AUTO_SUMMARIZATION_ENABLED = booleanPreferencesKey("auto_summarization_enabled")
        val AUTO_SUMMARIZATION_HOUR = stringPreferencesKey("auto_summarization_hour")
        val DATA_RETENTION_DAYS = stringPreferencesKey("data_retention_days")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SMART_NOTIFICATIONS_ENABLED = booleanPreferencesKey("smart_notifications_enabled")
        val SMART_NOTIFICATION_THRESHOLD = stringPreferencesKey("smart_notification_threshold")
        val SELECTED_OPENAI_MODEL = stringPreferencesKey("selected_openai_model")

        // SecurePreferences keys
        const val OPENAI_API_KEY = "openai_api_key"
    }

    override suspend fun hasCompletedOnboarding(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] ?: false
        }.first()
    }

    override suspend fun setOnboardingComplete() {
        context.dataStore.edit { preferences: androidx.datastore.preferences.core.MutablePreferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] = true
        }
    }

    override suspend fun getStorageLocation(): StorageHelper.StorageLocation {
        return context.dataStore.data.map { preferences ->
            val locationString = preferences[PreferencesKeys.STORAGE_LOCATION]
            when (locationString) {
                "EXTERNAL" -> StorageHelper.StorageLocation.EXTERNAL
                else -> StorageHelper.StorageLocation.INTERNAL
            }
        }.first()
    }

    override suspend fun setStorageLocation(location: StorageHelper.StorageLocation) {
        context.dataStore.edit { preferences: androidx.datastore.preferences.core.MutablePreferences ->
            preferences[PreferencesKeys.STORAGE_LOCATION] = location.name
        }
    }

    override suspend fun isWiFiOnlyDownload(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY_DOWNLOAD] ?: true // Default to WiFi-only
        }.first()
    }

    override suspend fun setWiFiOnlyDownload(wifiOnly: Boolean) {
        context.dataStore.edit { preferences: androidx.datastore.preferences.core.MutablePreferences ->
            preferences[PreferencesKeys.WIFI_ONLY_DOWNLOAD] = wifiOnly
        }
    }

    override fun observeSetupModelId(): Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[PreferencesKeys.SETUP_MODEL_ID] }.distinctUntilChanged()

    override suspend fun setSetupModelId(modelId: String?) {
        context.dataStore.edit { preferences ->
            if (modelId == null) {
                preferences.remove(PreferencesKeys.SETUP_MODEL_ID)
            } else {
                preferences[PreferencesKeys.SETUP_MODEL_ID] = modelId
            }
        }
    }

    // AI Provider preferences (Week 8)
    override suspend fun getAIProvider(): AIProvider {
        return context.dataStore.data.map { preferences ->
            val providerString = preferences[PreferencesKeys.AI_PROVIDER]
            when (providerString) {
                "OPENAI" -> AIProvider.OPENAI
                else -> AIProvider.LOCAL // Default to local (privacy-first)
            }
        }.first()
    }

    override suspend fun setAIProvider(provider: AIProvider) {
        context.dataStore.edit { preferences: androidx.datastore.preferences.core.MutablePreferences ->
            preferences[PreferencesKeys.AI_PROVIDER] = provider.name
        }
    }

    // OpenAI API Key (encrypted storage)
    override suspend fun getOpenAIApiKey(): String? {
        return withContext(Dispatchers.IO) {
            securePreferences.getString(PreferencesKeys.OPENAI_API_KEY)
        }
    }

    override suspend fun setOpenAIApiKey(apiKey: String) {
        withContext(Dispatchers.IO) {
            securePreferences.putString(PreferencesKeys.OPENAI_API_KEY, apiKey)
        }
    }

    override suspend fun clearOpenAIApiKey() {
        withContext(Dispatchers.IO) {
            securePreferences.remove(PreferencesKeys.OPENAI_API_KEY)
        }
    }

    // Auto-summarization preferences
    override suspend fun isAutoSummarizationEnabled(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_SUMMARIZATION_ENABLED] ?: false
        }.first()
    }

    override suspend fun setAutoSummarizationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SUMMARIZATION_ENABLED] = enabled
        }
    }

    override suspend fun getAutoSummarizationHour(): Int {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_SUMMARIZATION_HOUR]?.toIntOrNull() ?: 20 // Default to 8 PM
        }.first()
    }

    override suspend fun setAutoSummarizationHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SUMMARIZATION_HOUR] = hour.toString()
        }
    }

    // Data retention preferences
    override suspend fun getDataRetentionDays(): Int {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DATA_RETENTION_DAYS]?.toIntOrNull() ?: 30 // Default to 30 days
        }.first()
    }

    override suspend fun setDataRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATA_RETENTION_DAYS] = days.toString()
        }
    }

    // App lock
    override suspend fun isAppLockEnabled(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: true // Default to enabled
        }.first()
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    // Smart notification preferences
    override suspend fun isSmartNotificationsEnabled(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SMART_NOTIFICATIONS_ENABLED] ?: false // Default to disabled
        }.first()
    }

    override suspend fun setSmartNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    override suspend fun getSmartNotificationThreshold(): Float {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SMART_NOTIFICATION_THRESHOLD]?.toFloatOrNull() ?: 0.6f // Default to 0.6
        }.first()
    }

    override suspend fun setSmartNotificationThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_NOTIFICATION_THRESHOLD] = threshold.toString()
        }
    }

    // OpenAI Model Selection
    override suspend fun getSelectedOpenAIModel(): String {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SELECTED_OPENAI_MODEL] ?: "gpt-4o-mini" // Default to gpt-4o-mini
        }.first()
    }

    override suspend fun setSelectedOpenAIModel(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_OPENAI_MODEL] = modelId
        }
    }

    override suspend fun clearAll() {
        // Clear DataStore preferences
        context.dataStore.edit { preferences ->
            preferences.clear()
        }

        // Clear secrets (API key, PIN, lock state)
        withContext(Dispatchers.IO) {
            securePreferences.clear()
        }
    }
}
