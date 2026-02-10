package com.summarizer.app.worker

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduling of WorkManager tasks for auto-summarization.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Schedules daily auto-summarization at the specified hour (0-23).
     * If already scheduled, it will be rescheduled with the new time.
     */
    fun scheduleDailySummarization(hour: Int) {
        Timber.i("Scheduling daily auto-summarization at $hour:00")

        // Calculate initial delay until the target hour
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If target time is in the past today, schedule for tomorrow
            if (before(currentTime)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis

        Timber.d("Initial delay: ${initialDelay / 1000 / 60} minutes")

        // Create constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Need network for OpenAI or model download
            .setRequiresBatteryNotLow(true) // Don't run when battery is critically low
            .build()

        // Create periodic work request (runs once per day)
        val workRequest = PeriodicWorkRequestBuilder<AutoSummarizationWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag("auto_summarization")
            .build()

        // Schedule work (replace existing work with same name)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoSummarizationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Replace existing schedule
            workRequest
        )

        Timber.i("Daily auto-summarization scheduled successfully")
    }

    /**
     * Cancels the daily auto-summarization.
     */
    fun cancelDailySummarization() {
        Timber.i("Canceling daily auto-summarization")
        WorkManager.getInstance(context).cancelUniqueWork(AutoSummarizationWorker.WORK_NAME)
    }

    /**
     * Triggers an immediate one-time summarization (for testing).
     */
    fun triggerImmediateSummarization() {
        Timber.i("Triggering immediate auto-summarization")

        val workRequest = OneTimeWorkRequestBuilder<AutoSummarizationWorker>()
            .addTag("auto_summarization_manual")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * Schedules daily data retention cleanup.
     * Runs every day to delete old messages and summaries based on retention settings.
     */
    fun scheduleDailyDataRetention() {
        Timber.i("Scheduling daily data retention cleanup")

        // Create constraints (don't need network or battery for database cleanup)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // Create periodic work request (runs once per day)
        val workRequest = PeriodicWorkRequestBuilder<DataRetentionWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag("data_retention")
            .build()

        // Schedule work (replace existing work with same name)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DataRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Timber.i("Daily data retention cleanup scheduled successfully")
    }

    /**
     * Cancels the daily data retention cleanup.
     */
    fun cancelDailyDataRetention() {
        Timber.i("Canceling daily data retention cleanup")
        WorkManager.getInstance(context).cancelUniqueWork(DataRetentionWorker.WORK_NAME)
    }

    /**
     * Triggers an immediate one-time data retention cleanup (for testing).
     */
    fun triggerImmediateDataRetention() {
        Timber.i("Triggering immediate data retention cleanup")

        val workRequest = OneTimeWorkRequestBuilder<DataRetentionWorker>()
            .addTag("data_retention_manual")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
