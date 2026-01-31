package com.summarizer.app.domain.repository

import com.summarizer.app.util.StorageHelper

interface PreferencesRepository {
    suspend fun isFirstLaunch(): Boolean
    suspend fun setFirstLaunchComplete()
    suspend fun hasCompletedOnboarding(): Boolean
    suspend fun setOnboardingComplete()

    // Model download preferences
    suspend fun getStorageLocation(): StorageHelper.StorageLocation
    suspend fun setStorageLocation(location: StorageHelper.StorageLocation)
    suspend fun isWiFiOnlyDownload(): Boolean
    suspend fun setWiFiOnlyDownload(wifiOnly: Boolean)
}
