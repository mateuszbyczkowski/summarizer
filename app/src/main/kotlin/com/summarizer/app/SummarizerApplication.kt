package com.summarizer.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import com.summarizer.app.data.debug.DebugSampleData
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.ui.lock.AppLockManager
import com.summarizer.app.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SummarizerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workScheduler: WorkScheduler

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var debugSampleData: DebugSampleData

    @Inject
    lateinit var modelRepository: ModelRepository

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("Application started")

        appLockManager.start()

        workScheduler.scheduleDailyDataRetention()
        // KEEP: when WorkManager starts the process to run a due check, do not cancel it
        applicationScope.launch { workScheduler.refreshAutoSummarization(ExistingWorkPolicy.KEEP) }
        applicationScope.launch { modelRepository.refreshDownloadState() }

        // Development aid: a fixed sample chat for comparing models (debug builds only)
        if (BuildConfig.DEBUG) {
            applicationScope.launch { debugSampleData.seed() }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
