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
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.domain.repository.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker for data retention cleanup.
 *
 * Deletes messages older than the retention period, refreshes thread stats and removes threads
 * left with neither messages nor summaries. Summaries are kept forever.
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val messageDao: MessageDao,
    private val threadDao: ThreadDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "data_retention_work"
        private const val NOTIFICATION_CHANNEL_ID = "data_retention_channel"
        private const val NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = preferencesRepository.getDataRetentionDays()
            val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())

            // Summaries are kept forever; only raw messages expire
            val messagesDeleted = messageDao.deleteMessagesBefore(cutoffTimestamp)
            threadDao.refreshAllThreadStats()
            val threadsDeleted = threadDao.deleteEmptyThreads()

            Timber.i("Data retention cleanup: $messagesDeleted messages, $threadsDeleted threads deleted")
            if (messagesDeleted > 0) {
                createNotificationChannel()
                val resources = context.resources
                showNotification(
                    context.getString(R.string.retention_notification_title),
                    context.getString(
                        R.string.retention_notification_text,
                        resources.getQuantityString(R.plurals.retention_notification_messages, messagesDeleted, messagesDeleted),
                        resources.getQuantityString(R.plurals.days, retentionDays, retentionDays)
                    )
                )
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Data retention cleanup failed")
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.retention_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.retention_channel_description)
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
