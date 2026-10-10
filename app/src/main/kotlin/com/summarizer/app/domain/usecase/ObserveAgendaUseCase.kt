package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.model.ThreadOverview
import com.summarizer.app.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Dates and tasks from the latest summary of every followed thread, for the task list, the
 * "this week" card and the widget. Past items are included; screens pick the range they show.
 */
class ObserveAgendaUseCase @Inject constructor(
    private val threadRepository: ThreadRepository
) {

    operator fun invoke(): Flow<List<AgendaItem>> =
        threadRepository.getThreadOverviews()
            .map { buildAgenda(it) }
            .distinctUntilChanged()

    companion object {

        /**
         * Sorted by date and time; then items without a date: open tasks, done tasks, and dates
         * the summary could not pin to a day.
         */
        fun buildAgenda(overviews: List<ThreadOverview>): List<AgendaItem> =
            overviews
                .filter { it.thread.isFollowed }
                .flatMap { overview ->
                    val summary = overview.latestSummary ?: return@flatMap emptyList()
                    val threadName = overview.thread.threadName
                    val dates = summary.keyDates.mapIndexed { index, keyDate ->
                        AgendaItem(
                            threadId = summary.threadId,
                            threadName = threadName,
                            summaryId = summary.id,
                            kind = AgendaItem.Kind.DATE,
                            label = keyDate.label,
                            date = keyDate.date,
                            time = keyDate.time,
                            index = index,
                            done = false,
                            endTime = keyDate.endTime
                        )
                    }
                    val tasks = summary.actionItems.mapIndexed { index, item ->
                        AgendaItem(
                            threadId = summary.threadId,
                            threadName = threadName,
                            summaryId = summary.id,
                            kind = AgendaItem.Kind.TASK,
                            label = item.task,
                            date = item.due,
                            time = null,
                            index = index,
                            done = item.done
                        )
                    }
                    dates + tasks
                }
                .sortedWith(
                    compareBy<AgendaItem>(
                        { undatedRank(it) },
                        // ISO dates and HH:mm times sort correctly as text
                        { it.date },
                        { it.time ?: "" },
                        { it.kind },
                        { it.threadName }
                    )
                )

        private fun undatedRank(item: AgendaItem): Int = when {
            item.date != null -> 0
            item.kind == AgendaItem.Kind.TASK && !item.done -> 1
            item.kind == AgendaItem.Kind.TASK -> 2
            else -> 3
        }
    }
}
