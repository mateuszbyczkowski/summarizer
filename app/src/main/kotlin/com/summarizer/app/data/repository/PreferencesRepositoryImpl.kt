package com.summarizer.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {

    private object PreferencesKeys {
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val STORAGE_LOCATION = stringPreferencesKey("storage_location")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
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
}
