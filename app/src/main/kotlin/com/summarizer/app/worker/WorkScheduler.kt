package com.summarizer.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduling of WorkManager tasks.
 *
 * Auto-summarization runs as a one-time job at the top of every hour that re-schedules itself.
 * Each run summarizes the threads whose scheduled hour has passed since their last summary, so
 * runs delayed by Doze or cut short by the system catch up later, and DST changes do not shift
 * the schedule.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val threadSettingsRepository: ThreadSettingsRepository
) {

    /**
     * Keep the hourly auto-summarization check scheduled while any thread may need it (global
     * setting on, or a thread enabled explicitly); cancel it otherwise.
     *
     * @param policy REPLACE after settings changes (new constraints apply immediately), KEEP at
     *   app start (never cancel a due or running check), APPEND_OR_REPLACE from the worker itself
     *   (the next check is chained after the running one)
     */
    suspend fun refreshAutoSummarization(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val needed = preferencesRepository.isAutoSummarizationEnabled() ||
            threadSettingsRepository.getAllSettings().any { it.autoSummarizationEnabled == true }
        if (!needed) {
            cancelAutoSummarization()
            return
        }

        val now = ZonedDateTime.now()
        val nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val requiresNetwork = preferencesRepository.getAIProvider() == AIProvider.OPENAI

        val workRequest = OneTimeWorkRequestBuilder<AutoSummarizationWorker>()
            .setInitialDelay(Duration.between(now, nextHour).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag("auto_summarization")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AutoSummarizationWorker.WORK_NAME,
            policy,
            workRequest
        )
        Timber.d("Next auto-summarization check at $nextHour")
    }

    fun cancelAutoSummarization() {
        WorkManager.getInstance(context).cancelUniqueWork(AutoSummarizationWorker.WORK_NAME)
    }

    /**
     * Schedules daily data retention cleanup.
     */
    fun scheduleDailyDataRetention() {
        val workRequest = PeriodicWorkRequestBuilder<DataRetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag("data_retention")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DataRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
