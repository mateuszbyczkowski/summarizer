package com.summarizer.app.domain.usecase

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
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
 * Screens observe [stateFor]; results land in the database.
 */
@Singleton
class SummaryGenerationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed interface State {
        data object Running : State
        data class Failed(val message: String, val nothingNew: Boolean) : State
    }

    private val workManager get() = WorkManager.getInstance(context)

    /** Failed jobs the user already dismissed; WorkManager keeps finished jobs around for a while. */
    private val dismissed = MutableStateFlow<Set<UUID>>(emptySet())

    fun stateFor(threadId: String): Flow<State?> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(SummaryGenerationWorker.uniqueWorkName(threadId)),
            dismissed
        ) { infos, dismissedIds ->
            val info = infos.lastOrNull() ?: return@combine null
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> State.Running
                WorkInfo.State.FAILED -> if (info.id in dismissedIds) null else State.Failed(
                    message = info.outputData.getString(SummaryGenerationWorker.KEY_ERROR).orEmpty(),
                    nothingNew = info.outputData.getBoolean(SummaryGenerationWorker.KEY_NOTHING_NEW, false)
                )
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
            }
        }.distinctUntilChanged()

    fun generate(threadId: String) {
        val request = OneTimeWorkRequestBuilder<SummaryGenerationWorker>()
            .setInputData(workDataOf(SummaryGenerationWorker.KEY_THREAD_ID to threadId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("summary_generation")
            .build()
        // KEEP: a second tap while a summary is generating does nothing
        workManager.enqueueUniqueWork(
            SummaryGenerationWorker.uniqueWorkName(threadId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun dismissError(threadId: String) {
        val latest = workManager
            .getWorkInfosForUniqueWork(SummaryGenerationWorker.uniqueWorkName(threadId))
            .get()
            .lastOrNull()
            ?: return
        if (latest.state == WorkInfo.State.FAILED) dismissed.update { it + latest.id }
    }
}
