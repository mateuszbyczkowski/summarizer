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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.summarizer.app.MainActivity
import com.summarizer.app.R
import com.summarizer.app.domain.model.GenerationStage
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.GenerateSummaryUseCase
import com.summarizer.app.domain.usecase.NothingToSummarizeException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Generates one summary the user asked for, as a foreground job so it survives the user
 * switching to another app, then posts a "summary ready" notification that opens it.
 *
 * Publishes progress ([KEY_STAGE], [KEY_STARTED_AT], [KEY_PARTIAL_TEXT]) for the screens; the
 * ongoing notification can cancel the job. The ready notification shows the first task, but
 * only when the phone is unlocked: on the lock screen a public version without content is shown.
 */
@HiltWorker
class SummaryGenerationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    private val threadRepository: ThreadRepository,
    private val preferencesRepository: PreferencesRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_THREAD_ID = "thread_id"

        /** Input: summarize the whole conversation this time, whatever the thread's mode */
        const val KEY_FULL = "full"
        const val KEY_ERROR = "error"
        const val KEY_NOTHING_NEW = "nothing_new"

        /** Progress: [GenerationStage] name */
        const val KEY_STAGE = "stage"

        /** Progress: when generation started (epoch millis) */
        const val KEY_STARTED_AT = "started_at"

        /** Progress: the overview written so far */
        const val KEY_PARTIAL_TEXT = "partial_text"

        // WorkManager data is limited to 10 KB
        private const val MAX_PARTIAL_CHARS = 2_000
        // Streaming produces a token every few milliseconds; the screen needs a few updates per second
        private const val PROGRESS_INTERVAL_MS = 250L

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

        // The user asked for this summary, so re-summarize even without new messages.
        // Cancelling the job (notification action, coordinator) cancels this coroutine, which
        // stops the engine.
        return generateWithProgress().fold(
            onSuccess = { summary ->
                notifyReady(threadName, summary)
                Result.success()
            },
            onFailure = { error ->
                val nothingNew = error is NothingToSummarizeException
                // The error text is technical English: shown only in debug builds, never in notifications
                val message = error.message.orEmpty()
                notifyResult(
                    threadName,
                    title = context.getString(
                        if (nothingNew) R.string.summary_notification_nothing_new_title
                        else R.string.summary_notification_failed_title
                    ),
                    text = if (nothingNew) null else context.getString(R.string.summary_notification_failed_text)
                )
                Result.failure(workDataOf(KEY_ERROR to message, KEY_NOTHING_NEW to nothingNew))
            }
        )
    }

    private data class Progress(val stage: GenerationStage, val partialText: String?)

    /** Runs the use case while publishing its progress, throttled, as WorkManager progress. */
    private suspend fun generateWithProgress(): kotlin.Result<Summary> = coroutineScope {
        val startedAt = System.currentTimeMillis()
        val progress = MutableStateFlow(Progress(GenerationStage.PREPARING, null))
        val publisher = launch {
            // StateFlow keeps only the latest value, so pausing here skips intermediate updates
            progress.collect { (stage, partialText) ->
                setProgress(
                    workDataOf(
                        KEY_STAGE to stage.name,
                        KEY_STARTED_AT to startedAt,
                        KEY_PARTIAL_TEXT to partialText?.take(MAX_PARTIAL_CHARS)
                    )
                )
                delay(PROGRESS_INTERVAL_MS)
            }
        }
        try {
            generateSummaryUseCase.execute(
                threadId,
                allowRepeat = true,
                forceFull = inputData.getBoolean(KEY_FULL, false)
            ) { stage, partialOverview ->
                progress.value = Progress(stage, partialOverview)
            }
        } finally {
            publisher.cancel()
        }
    }

    private fun foregroundInfo(threadName: String?): ForegroundInfo {
        val title = context.getString(R.string.summary_notification_progress_title)
        val notification = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(threadName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openSummaryIntent())
            .addAction(
                0,
                context.getString(R.string.summary_notification_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(id)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(PROGRESS_CHANNEL_ID, context.getString(R.string.summary_notification_progress_public)))
            .build()
        return ForegroundInfo(progressNotificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    /**
     * "Summary ready · 2 tasks" with the first open task, and actions to show or copy it. With the
     * app lock on, the notification names only the chat: its content and the Copy action would
     * let anyone holding the unlocked phone read the summary without passing the app lock.
     */
    private suspend fun notifyReady(threadName: String?, summary: Summary) {
        val openTasks = summary.actionItems.filterNot { it.done }
        val title = if (openTasks.isEmpty()) {
            context.getString(R.string.summary_notification_ready_title)
        } else {
            context.resources.getQuantityString(R.plurals.summary_notification_ready_tasks, openTasks.size, openTasks.size)
        }
        val chatText = threadName?.let { context.getString(R.string.summary_notification_ready_text, it) }
        if (preferencesRepository.isAppLockEnabled()) {
            notifyResult(threadName, title, chatText) {
                addAction(0, context.getString(R.string.summary_notification_show), openSummaryIntent())
            }
            return
        }
        notifyResult(threadName, title, openTasks.firstOrNull()?.task ?: chatText) {
            setSubText(threadName)
                .addAction(0, context.getString(R.string.summary_notification_show), openSummaryIntent())
                .addAction(0, context.getString(R.string.summary_notification_copy), copySummaryIntent(summary.id))
        }
    }

    private suspend fun notifyResult(
        threadName: String?,
        title: String,
        text: String?,
        extras: NotificationCompat.Builder.() -> NotificationCompat.Builder = { this }
    ) {
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
            // Lock screen: only the generic title, no chat name or summary content
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(RESULT_CHANNEL_ID, context.getString(R.string.summary_notification_ready_title)))
            .extras()
            .build()
        notificationManager.notify(resultNotificationId(), notification)
    }

    private fun publicNotification(channelId: String, title: String): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .build()

    private fun copySummaryIntent(summaryId: Long): PendingIntent {
        val intent = Intent(context, CopySummaryReceiver::class.java).apply {
            putExtra(CopySummaryReceiver.EXTRA_SUMMARY_ID, summaryId)
            putExtra(CopySummaryReceiver.EXTRA_NOTIFICATION_ID, resultNotificationId())
        }
        return PendingIntent.getBroadcast(
            context,
            resultNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
