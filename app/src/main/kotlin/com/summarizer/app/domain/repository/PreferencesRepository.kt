package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.AIProvider
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

    // AI Provider preferences (Week 8)
    suspend fun getAIProvider(): AIProvider
    suspend fun setAIProvider(provider: AIProvider)

    // OpenAI API Key (stored in EncryptedSharedPreferences)
    suspend fun getOpenAIApiKey(): String?
    suspend fun setOpenAIApiKey(apiKey: String)
    suspend fun clearOpenAIApiKey()

    // OpenAI Model Selection
    suspend fun getSelectedOpenAIModel(): String // Returns model ID (e.g., "gpt-4o-mini")
    suspend fun setSelectedOpenAIModel(modelId: String)

    // Auto-summarization preferences
    suspend fun isAutoSummarizationEnabled(): Boolean
    suspend fun setAutoSummarizationEnabled(enabled: Boolean)
    suspend fun getAutoSummarizationHour(): Int // 0-23, default 20 (8 PM)
    suspend fun setAutoSummarizationHour(hour: Int)

    // Data retention preferences
    suspend fun getDataRetentionDays(): Int // Default 30 days
    suspend fun setDataRetentionDays(days: Int)

    // Biometric authentication preferences
    suspend fun isBiometricEnabled(): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)

    // Smart notification preferences
    suspend fun isSmartNotificationsEnabled(): Boolean
    suspend fun setSmartNotificationsEnabled(enabled: Boolean)
    suspend fun getSmartNotificationThreshold(): Float // 0.0-1.0, default 0.6
    suspend fun setSmartNotificationThreshold(threshold: Float)

    // Reset all preferences
    suspend fun clearAll()
}
