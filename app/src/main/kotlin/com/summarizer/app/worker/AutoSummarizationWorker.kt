package com.summarizer.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.summarizer.app.R
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.GenerateSummaryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Background worker for daily auto-summarization of followed threads.
 *
 * This worker:
 * 1. Fetches all followed threads
 * 2. Generates summaries for each thread
 * 3. Shows a notification when complete
 */
@HiltWorker
class AutoSummarizationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val threadRepository: ThreadRepository,
    private val generateSummaryUseCase: GenerateSummaryUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "auto_summarization_work"
        private const val NOTIFICATION_CHANNEL_ID = "auto_summary_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.i("Starting auto-summarization for followed threads")

            // Create notification channel (safe to call repeatedly)
            createNotificationChannel()

            // Get all followed threads
            val allThreads = threadRepository.getAllThreads().first()
            val followedThreads = allThreads.filter { it.isFollowed }

            Timber.d("Found ${followedThreads.size} followed threads")

            if (followedThreads.isEmpty()) {
                Timber.i("No followed threads to summarize")
                showNotification("No followed threads", "No threads are currently being followed.")
                return Result.success()
            }

            // Summarize each followed thread
            var successCount = 0
            var failureCount = 0

            followedThreads.forEach { thread ->
                try {
                    Timber.d("Generating summary for thread: ${thread.threadId}")
                    val result = generateSummaryUseCase.execute(thread.threadId)

                    if (result.isSuccess) {
                        successCount++
                        Timber.d("Successfully generated summary for thread: ${thread.threadId}")
                    } else {
                        failureCount++
                        Timber.w("Failed to generate summary for thread: ${thread.threadId}", result.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    failureCount++
                    Timber.e(e, "Error generating summary for thread: ${thread.threadId}")
                }
            }

            // Show completion notification
            val title = "Auto-summarization complete"
            val message = when {
                failureCount == 0 -> "Successfully summarized $successCount thread(s)"
                successCount == 0 -> "Failed to summarize $failureCount thread(s)"
                else -> "Summarized $successCount thread(s), $failureCount failed"
            }

            showNotification(title, message)

            Timber.i("Auto-summarization completed: $successCount success, $failureCount failures")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Auto-summarization worker failed")
            showNotification("Auto-summarization failed", "An error occurred during summarization")
            Result.failure()
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
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
