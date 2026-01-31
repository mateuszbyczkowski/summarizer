package com.summarizer.app.domain.repository

interface PreferencesRepository {
    suspend fun isFirstLaunch(): Boolean
    suspend fun setFirstLaunchComplete()
    suspend fun hasCompletedOnboarding(): Boolean
    suspend fun setOnboardingComplete()
}
