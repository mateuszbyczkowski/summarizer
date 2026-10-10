package com.summarizer.app.data.repository

import android.app.NotificationManager
import android.content.Context
import com.summarizer.app.data.download.ModelDownloadManager
import com.summarizer.app.data.local.database.AppDatabase
import com.summarizer.app.data.local.preferences.RecentSearchesStore
import com.summarizer.app.domain.ai.AIEngine
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import com.summarizer.app.util.StorageHelper
import com.summarizer.app.worker.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes everything the app stores: database, downloaded models, preferences, recent searches
 * and the PIN.
 */
@Singleton
class AppDataResetter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val aiEngine: AIEngine,
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val workScheduler: WorkScheduler,
    private val recentSearchesStore: RecentSearchesStore,
    private val modelDownloadManager: ModelDownloadManager,
    private val summaryGenerationCoordinator: SummaryGenerationCoordinator
) {

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        workScheduler.cancelAutoSummarization()
        // A summary in progress could reload the model and save into the cleared database
        summaryGenerationCoordinator.cancelAllBlocking()
        aiEngine.unloadModel()
        // A running download would keep writing into the deleted folder and claim the next one
        modelDownloadManager.cancelAll()
        database.clearAllTables()
        StorageHelper.StorageLocation.entries.forEach { location ->
            StorageHelper.getModelStorageDirectory(context, location).deleteRecursively()
        }
        preferencesRepository.clearAll()
        recentSearchesStore.clear()
        authRepository.clearPin()
        // Their taps would open chats that no longer exist
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
