package com.summarizer.app.domain.model

/**
 * A thread with what the home screen shows about it.
 *
 * @property latestSummary The most recent summary, if any
 * @property newSinceSummary Messages newer than the latest summary's last message; all messages
 *           when the thread has no summary
 * @property openTasks Tasks of the latest summary that are not done
 */
data class ThreadOverview(
    val thread: Thread,
    val latestSummary: Summary?,
    val newSinceSummary: Int,
    val openTasks: Int
)
