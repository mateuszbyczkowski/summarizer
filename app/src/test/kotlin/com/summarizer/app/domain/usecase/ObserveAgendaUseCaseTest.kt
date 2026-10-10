package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.model.ThreadOverview
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveAgendaUseCaseTest {

    private fun overview(id: String, followed: Boolean, summary: Summary?) = ThreadOverview(
        thread = Thread(threadId = id, threadName = "Chat $id", lastMessageTimestamp = 0, isFollowed = followed),
        latestSummary = summary,
        newSinceSummary = 0,
        openTasks = summary?.actionItems?.count { !it.done } ?: 0
    )

    private fun summary(id: Long, threadId: String, keyDates: List<KeyDate>, actions: List<ActionItem>) = Summary(
        id = id,
        threadId = threadId,
        threadName = "old name",
        keyTopics = emptyList(),
        actionItems = actions,
        announcements = emptyList(),
        participantHighlights = emptyList(),
        messageCount = 1,
        startTimestamp = 0,
        endTimestamp = 0,
        keyDates = keyDates
    )

    @Test
    fun `items of followed threads are sorted by date with undated items last`() {
        val agenda = ObserveAgendaUseCase.buildAgenda(
            listOf(
                overview(
                    "a", followed = true,
                    summary = summary(
                        1, "a",
                        keyDates = listOf(KeyDate("Zebranie", "2026-10-21", "18:00"), KeyDate("Basen", null, null, "w środę")),
                        actions = listOf(
                            ActionItem("Przelew", due = "2026-10-15"),
                            ActionItem("Zgody"),
                            ActionItem("Bluza", done = true)
                        )
                    )
                ),
                overview(
                    "b", followed = true,
                    summary = summary(2, "b", keyDates = listOf(KeyDate("Wycieczka", "2026-10-21", "07:30")), actions = emptyList())
                ),
                overview("c", followed = false, summary = summary(3, "c", listOf(KeyDate("Ukryte", "2026-10-14")), emptyList())),
                overview("d", followed = true, summary = null)
            )
        )

        assertEquals(listOf("Przelew", "Wycieczka", "Zebranie", "Zgody", "Bluza", "Basen"), agenda.map { it.label })
        assertEquals(
            AgendaItem("a", "Chat a", 1, AgendaItem.Kind.TASK, "Przelew", "2026-10-15", null, index = 0, done = false),
            agenda.first()
        )
        assertEquals(AgendaItem.Kind.DATE to 1, agenda.last().kind to agenda.last().index)
    }
}
