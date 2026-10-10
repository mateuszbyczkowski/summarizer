package com.summarizer.app.domain.usecase

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.summarizer.app.domain.model.GenerationStage
import com.summarizer.app.worker.SummaryGenerationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts on-demand summary generation as a [SummaryGenerationWorker] job. It keeps running
 * when the user leaves the app and posts a notification when done; at most one runs per thread.
 * Screens observe [stateFor] (including progress and the overview as it is written); results
 * land in the database.
 *
 * The overview being written stays in memory here ([publishPartialText]), never in WorkManager
 * progress: WorkManager stores progress in its own unencrypted database and logs it.
 */
@Singleton
class SummaryGenerationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed interface State {
        /**
         * @property startedAt When the job started (epoch millis); null while it waits to start
         * @property partialText The overview written so far, during [GenerationStage.WRITING]
         */
        data class Running(
            val stage: GenerationStage,
            val startedAt: Long?,
            val partialText: String?
        ) : State

        data class Failed(val message: String, val nothingNew: Boolean) : State
    }

    private companion object {
        const val TAG = "summary_generation"
        const val THREAD_TAG_PREFIX = "summary_thread:"
    }

    private val workManager get() = WorkManager.getInstance(context)

    /** Failed jobs the user already dismissed; WorkManager keeps finished jobs around for a while. */
    private val dismissed = MutableStateFlow<Set<UUID>>(emptySet())

    /** The overview written so far, by thread, while its summary is being written. */
    private val partialTexts = MutableStateFlow<Map<String, String>>(emptyMap())

    fun stateFor(threadId: String): Flow<State?> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(SummaryGenerationWorker.uniqueWorkName(threadId)),
            dismissed,
            partialTexts
        ) { infos, dismissedIds, partials ->
            val info = infos.lastOrNull() ?: return@combine null
            stateOf(info, dismissedIds, partials[threadId])
        }.distinctUntilChanged()

    /** Called by the summary job as the overview is written; null when the job ends. */
    fun publishPartialText(threadId: String, text: String?) {
        partialTexts.update { if (text.isNullOrBlank()) it - threadId else it + (threadId to text) }
    }

    /**
     * States of all threads with a summary job, from one WorkManager query (for lists such as
     * Home, instead of one [stateFor] flow per thread).
     */
    fun runningStates(): Flow<Map<String, State>> =
        combine(workManager.getWorkInfosByTagFlow(TAG), dismissed) { infos, dismissedIds ->
            infos.mapNotNull { info ->
                val threadId = info.tags.firstOrNull { it.startsWith(THREAD_TAG_PREFIX) }
                    ?.removePrefix(THREAD_TAG_PREFIX) ?: return@mapNotNull null
                stateOf(info, dismissedIds, partialText = null)?.let { threadId to it }
            }.toMap()
        }.distinctUntilChanged()

    private fun stateOf(info: WorkInfo, dismissedIds: Set<UUID>, partialText: String?): State? = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> runningState(info, partialText = null)
        WorkInfo.State.RUNNING -> runningState(info, partialText)
        WorkInfo.State.FAILED -> if (info.id in dismissedIds) null else State.Failed(
            message = info.outputData.getString(SummaryGenerationWorker.KEY_ERROR).orEmpty(),
            nothingNew = info.outputData.getBoolean(SummaryGenerationWorker.KEY_NOTHING_NEW, false)
        )
        WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
    }

    private fun runningState(info: WorkInfo, partialText: String?): State.Running {
        val progress = info.progress
        return State.Running(
            stage = progress.getString(SummaryGenerationWorker.KEY_STAGE)
                ?.let { name -> GenerationStage.entries.firstOrNull { it.name == name } }
                ?: GenerationStage.PREPARING,
            startedAt = progress.getLong(SummaryGenerationWorker.KEY_STARTED_AT, 0L).takeIf { it > 0 },
            partialText = partialText
        )
    }

    /**
     * Summarizes the thread in the background. By default it follows the thread's mode
     * (incremental or full); [full] summarizes the whole conversation once without changing it.
     */
    fun generate(threadId: String, full: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SummaryGenerationWorker>()
            .setInputData(
                workDataOf(
                    SummaryGenerationWorker.KEY_THREAD_ID to threadId,
                    SummaryGenerationWorker.KEY_FULL to full
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag(THREAD_TAG_PREFIX + threadId)
            .build()
        // KEEP: a second tap while a summary is generating does nothing
        workManager.enqueueUniqueWork(
            SummaryGenerationWorker.uniqueWorkName(threadId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Stops the thread's summary if one is generating; the job ends as cancelled, without a result. */
    fun cancel(threadId: String) {
        workManager.cancelUniqueWork(SummaryGenerationWorker.uniqueWorkName(threadId))
    }

    /** Cancels every summary job; blocks until WorkManager has recorded it (call off the main thread). */
    fun cancelAllBlocking() {
        workManager.cancelAllWorkByTag(TAG).result.get()
    }

    fun dismissError(threadId: String) {
        val latest = workManager
            .getWorkInfosForUniqueWork(SummaryGenerationWorker.uniqueWorkName(threadId))
            .get()
            .lastOrNull()
            ?: return
        if (latest.state == WorkInfo.State.FAILED) {
            dismissed.update { it + latest.id }
            // Finished jobs are stored by WorkManager; prune them so the error does not return
            // after a restart
            workManager.pruneWork()
        }
    }
}
