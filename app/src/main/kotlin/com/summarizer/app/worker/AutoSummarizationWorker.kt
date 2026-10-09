package com.summarizer.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.summarizer.app.MainActivity
import com.summarizer.app.R
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import com.summarizer.app.domain.usecase.GenerateSummaryUseCase
import com.summarizer.app.domain.usecase.NothingToSummarizeException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Hourly auto-summarization check (see [WorkScheduler]).
 *
 * Summarizes every followed thread whose effective schedule (per-thread settings, falling back
 * to the global ones) has passed since its last summary, then schedules the next check.
 */
@HiltWorker
class AutoSummarizationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val threadRepository: ThreadRepository,
    private val threadSettingsRepository: ThreadSettingsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    private val workScheduler: WorkScheduler
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "auto_summarization_work"
        private const val NOTIFICATION_CHANNEL_ID = "auto_summary_channel"
        private const val NOTIFICATION_ID = 1001
        private const val DISABLED_HOUR = -1
    }

    override suspend fun doWork(): Result {
        try {
            val dueThreads = findDueThreads()
            if (dueThreads.isEmpty()) return Result.success()

            var successCount = 0
            var failureCount = 0
            for (thread in dueThreads) {
                generateSummaryUseCase.execute(thread.threadId)
                    .onSuccess { successCount++ }
                    .onFailure { error ->
                        if (error !is NothingToSummarizeException) {
                            failureCount++
                            Timber.w(error, "Auto-summary failed for thread ${thread.threadId}")
                        }
                    }
            }

            if (successCount > 0 || failureCount > 0) {
                createNotificationChannel()
                showNotification(
                    "Auto-summarization complete",
                    when {
                        failureCount == 0 -> "Summarized $successCount thread(s)"
                        successCount == 0 -> "Failed to summarize $failureCount thread(s)"
                        else -> "Summarized $successCount thread(s), $failureCount failed"
                    }
                )
            }
            Timber.i("Auto-summarization run: $successCount summarized, $failureCount failed")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Auto-summarization worker failed")
            return Result.success()
        } finally {
            withContext(NonCancellable) {
                workScheduler.refreshAutoSummarization(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
    }

    private suspend fun findDueThreads(): List<Thread> {
        val now = ZonedDateTime.now()
        val globalEnabled = preferencesRepository.isAutoSummarizationEnabled()
        val globalHour = preferencesRepository.getAutoSummarizationHour()
        val settingsByThread = threadSettingsRepository.getAllSettings().associateBy { it.threadId }

        return threadRepository.getFollowedThreads().first().filter { thread ->
            val settings = settingsByThread[thread.threadId]
            val enabled = settings?.autoSummarizationEnabled ?: globalEnabled
            val hour = settings?.summaryScheduleHour ?: globalHour
            if (!enabled || hour == DISABLED_HOUR) return@filter false

            // Due once today's scheduled hour has passed and the thread was not summarized since.
            // Later checks the same day catch up on delayed or interrupted runs.
            val scheduled = now.truncatedTo(ChronoUnit.DAYS).withHour(hour)
            val lastSummarizedAt = settings?.lastSummarizedAt ?: 0L
            !now.isBefore(scheduled) && lastSummarizedAt < scheduled.toInstant().toEpochMilli()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Auto-Summarization",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for daily auto-summarization"
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, message: String) {
        // Create intent to open thread list when notification is clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_THREADS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
