package com.summarizer.app.ui.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.summarizer.app.worker.WorkScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Re-renders the "Upcoming" widget just after midnight, so "this week" and the urgent tint
 * follow the new day, then schedules itself for the next midnight while a widget is placed.
 */
@HiltWorker
class UpcomingWidgetMidnightWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val updater: UpcomingWidgetUpdater,
    private val workScheduler: WorkScheduler
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!updater.hasWidgets()) return Result.success()
        updater.refresh()
        // Chained after this run, so the unique name stays taken until the next one is due
        workScheduler.scheduleWidgetMidnightRefresh(ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "upcoming_widget_midnight_refresh"
    }
}
