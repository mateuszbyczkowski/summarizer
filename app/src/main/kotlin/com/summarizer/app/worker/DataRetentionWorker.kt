package com.summarizer.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.summarizer.app.R
import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.database.dao.SummaryDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.domain.repository.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker for data retention cleanup.
 *
 * This worker:
 * 1. Deletes messages older than retention period (keeps at least 30 recent per thread)
 * 2. Deletes summaries older than retention period
 * 3. Updates thread message counts
 * 4. Deletes empty threads
 * 5. Shows a notification when complete
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val messageDao: MessageDao,
    private val summaryDao: SummaryDao,
    private val threadDao: ThreadDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "data_retention_work"
        private const val NOTIFICATION_CHANNEL_ID = "data_retention_channel"
        private const val NOTIFICATION_ID = 1002
        private const val MIN_MESSAGES_TO_KEEP = 30
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.i("Starting data retention cleanup")

            // Create notification channel (safe to call repeatedly)
            createNotificationChannel()

            // Get retention period from preferences
            val retentionDays = preferencesRepository.getDataRetentionDays()
            Timber.d("Data retention period: $retentionDays days")

            // Calculate cutoff timestamp (messages older than this will be deleted)
            val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            Timber.d("Cutoff timestamp: $cutoffTimestamp (deleting data before this)")

            var totalMessagesDeleted = 0
            var totalSummariesDeleted = 0
            var totalThreadsDeleted = 0

            // Step 1: Clean up messages for each thread (keeping at least MIN_MESSAGES_TO_KEEP)
            val allThreadIds = messageDao.getAllThreadIds()
            Timber.d("Found ${allThreadIds.size} threads with messages")

            for (threadId in allThreadIds) {
                try {
                    // Count messages before deletion
                    val messagesBefore = messageDao.getMessageCount(threadId)

                    // Delete old messages, keeping at least MIN_MESSAGES_TO_KEEP recent ones
                    messageDao.deleteOldMessagesForThread(threadId, cutoffTimestamp, MIN_MESSAGES_TO_KEEP)

                    // Count messages after deletion
                    val messagesAfter = messageDao.getMessageCount(threadId)
                    val messagesDeleted = messagesBefore - messagesAfter

                    if (messagesDeleted > 0) {
                        totalMessagesDeleted += messagesDeleted
                        Timber.d("Thread $threadId: Deleted $messagesDeleted messages (kept $messagesAfter)")

                        // Update thread message count and find new last message timestamp
                        val recentMessages = messageDao.getRecentMessagesForThread(threadId, 1)
                        if (recentMessages.isNotEmpty()) {
                            threadDao.updateThreadStats(threadId, messagesAfter, recentMessages[0].timestamp)
                        } else {
                            // No messages left, set count to 0
                            threadDao.updateThreadStats(threadId, 0, 0)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up thread: $threadId")
                }
            }

            // Step 2: Delete old summaries
            summaryDao.deleteSummariesBefore(cutoffTimestamp)
            // Note: We can't easily count deleted summaries without querying first
            Timber.d("Deleted summaries older than cutoff timestamp")

            // Step 3: Delete empty threads (threads with 0 messages)
            totalThreadsDeleted = threadDao.deleteEmptyThreads()
            if (totalThreadsDeleted > 0) {
                Timber.d("Deleted $totalThreadsDeleted empty threads")
            }

            // Show completion notification
            val message = buildString {
                append("Cleanup complete: ")
                if (totalMessagesDeleted > 0) append("$totalMessagesDeleted messages")
                if (totalThreadsDeleted > 0) {
                    if (totalMessagesDeleted > 0) append(", ")
                    append("$totalThreadsDeleted threads")
                }
                if (totalMessagesDeleted == 0 && totalThreadsDeleted == 0) {
                    append("No old data to clean")
                }
            }

            showNotification("Data Retention Cleanup", message)

            Timber.i("Data retention cleanup completed: $totalMessagesDeleted messages, $totalThreadsDeleted threads deleted")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Data retention cleanup failed")
            showNotification("Cleanup failed", "An error occurred during data cleanup")
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Data Retention",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for data retention cleanup"
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
