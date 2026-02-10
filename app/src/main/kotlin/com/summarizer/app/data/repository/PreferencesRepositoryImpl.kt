package com.summarizer.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {

    // EncryptedSharedPreferences for sensitive data (API keys)
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private object PreferencesKeys {
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val STORAGE_LOCATION = stringPreferencesKey("storage_location")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AUTO_SUMMARIZATION_ENABLED = booleanPreferencesKey("auto_summarization_enabled")
        val AUTO_SUMMARIZATION_HOUR = stringPreferencesKey("auto_summarization_hour")
        val DATA_RETENTION_DAYS = stringPreferencesKey("data_retention_days")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SMART_NOTIFICATIONS_ENABLED = booleanPreferencesKey("smart_notifications_enabled")
        val SMART_NOTIFICATION_THRESHOLD = stringPreferencesKey("smart_notification_threshold")

        // EncryptedSharedPreferences keys (for API key)
        const val OPENAI_API_KEY = "openai_api_key"
    }

    override suspend fun isFirstLaunch(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH] ?: true
        }.first()
    }

    override suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { preferences: androidx.datastore.preferences.core.MutablePreferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH] = false
        }
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
            encryptedPrefs.getString(PreferencesKeys.OPENAI_API_KEY, null)
        }
    }

    override suspend fun setOpenAIApiKey(apiKey: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(PreferencesKeys.OPENAI_API_KEY, apiKey).apply()
        }
    }

    override suspend fun clearOpenAIApiKey() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().remove(PreferencesKeys.OPENAI_API_KEY).apply()
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

    // Biometric authentication preferences
    override suspend fun isBiometricEnabled(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_ENABLED] ?: false // Default to disabled
        }.first()
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_ENABLED] = enabled
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

    override suspend fun clearAll() {
        // Clear DataStore preferences
        context.dataStore.edit { preferences ->
            preferences.clear()
        }

        // Clear encrypted preferences
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().clear().apply()
        }
    }
}
