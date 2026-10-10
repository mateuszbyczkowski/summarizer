package com.summarizer.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.summarizer.app.MainActivity
import com.summarizer.app.R
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.GenerateSummaryUseCase
import com.summarizer.app.domain.usecase.NothingToSummarizeException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Generates one summary the user asked for, as a foreground job so it survives the user
 * switching to another app, then posts a "summary ready" notification that opens it.
 *
 * Notifications only name the thread; summary content never appears on the lock screen.
 */
@HiltWorker
class SummaryGenerationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    private val threadRepository: ThreadRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_THREAD_ID = "thread_id"
        const val KEY_ERROR = "error"
        const val KEY_NOTHING_NEW = "nothing_new"

        private const val PROGRESS_CHANNEL_ID = "summary_progress"
        private const val RESULT_CHANNEL_ID = "summary_result"

        fun uniqueWorkName(threadId: String) = "summary_$threadId"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val threadId: String get() = inputData.getString(KEY_THREAD_ID).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(threadName = null)

    override suspend fun doWork(): Result {
        if (threadId.isEmpty()) return Result.failure()
        createChannels()
        val threadName = threadRepository.getThread(threadId)?.threadName

        try {
            setForeground(foregroundInfo(threadName))
        } catch (e: Exception) {
            // Started while the app was already in the background; run as a normal job
            Timber.w(e, "Could not promote summary generation to foreground")
        }

        // The user asked for this summary, so re-summarize even without new messages
        return generateSummaryUseCase.execute(threadId, allowRepeat = true).fold(
            onSuccess = {
                notifyResult(
                    threadName,
                    title = context.getString(R.string.summary_notification_ready_title),
                    text = threadName?.let { context.getString(R.string.summary_notification_ready_text, it) }
                )
                Result.success()
            },
            onFailure = { error ->
                val nothingNew = error is NothingToSummarizeException
                val message = error.message ?: context.getString(R.string.summary_notification_failed_text)
                notifyResult(
                    threadName,
                    title = context.getString(
                        if (nothingNew) R.string.summary_notification_nothing_new_title
                        else R.string.summary_notification_failed_title
                    ),
                    text = message
                )
                Result.failure(workDataOf(KEY_ERROR to message, KEY_NOTHING_NEW to nothingNew))
            }
        )
    }

    private fun foregroundInfo(threadName: String?): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.summary_notification_progress_title))
            .setContentText(threadName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openSummaryIntent())
            .build()
        return ForegroundInfo(progressNotificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private suspend fun notifyResult(threadName: String?, title: String, text: String?) {
        // The summary screen already shows the result when the user is in the app
        val appVisible = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (appVisible) return

        val notification: Notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text ?: threadName)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openSummaryIntent())
            .build()
        notificationManager.notify(resultNotificationId(), notification)
    }

    private fun openSummaryIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SUMMARY_THREAD_ID, threadId)
        }
        return PendingIntent.getActivity(
            context,
            resultNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                context.getString(R.string.summary_channel_progress),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                RESULT_CHANNEL_ID,
                context.getString(R.string.summary_channel_result),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun progressNotificationId() = "summary_progress_$threadId".hashCode()

    private fun resultNotificationId() = "summary_result_$threadId".hashCode()
}
