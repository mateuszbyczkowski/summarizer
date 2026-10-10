package com.summarizer.app.domain.usecase

import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.usecase.AgendaSchedule.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaScheduleTest {

    private val today = LocalDate.of(2026, 10, 13)

    private fun item(label: String, kind: AgendaItem.Kind, date: String?, done: Boolean = false) = AgendaItem(
        threadId = "t",
        threadName = "Chat",
        summaryId = 1,
        kind = kind,
        label = label,
        date = date,
        time = null,
        index = 0,
        done = done
    )

    private fun task(label: String, date: String?, done: Boolean = false) = item(label, AgendaItem.Kind.TASK, date, done)
    private fun date(label: String, date: String?) = item(label, AgendaItem.Kind.DATE, date)

    @Test
    fun `items are grouped by date and past items drop out`() {
        val items = listOf(
            task("Old open", "2026-10-01"),
            task("Old done", "2026-10-10", done = true),
            date("Past event", "2026-10-12"),
            task("Today", "2026-10-13"),
            date("Tomorrow", "2026-10-14"),
            date("Sunday", "2026-10-19"),
            date("Next week", "2026-10-20"),
            task("Undated", null),
            date("Unresolved", "w środę")
        )

        val groups = AgendaSchedule.group(items, today)

        assertEquals(
            listOf(Bucket.OVERDUE, Bucket.TODAY, Bucket.TOMORROW, Bucket.THIS_WEEK, Bucket.LATER, Bucket.NO_DATE),
            groups.map { it.first }
        )
        assertEquals(listOf("Old open"), groups[0].second.map { it.label })
        assertEquals(listOf("Sunday"), groups[3].second.map { it.label })
        assertEquals(listOf("Undated", "Unresolved"), groups[5].second.map { it.label })
        assertNull(AgendaSchedule.bucketOf(date("Past event", "2026-10-12"), today))
    }

    @Test
    fun `this week shows dates ahead and open tasks due soon, at most three`() {
        val items = listOf(
            task("Overdue long ago", "2026-09-01"),
            task("Overdue", "2026-10-11"),
            date("Past event", "2026-10-12"),
            task("Done", "2026-10-14", done = true),
            date("Trip", "2026-10-17"),
            task("Payment", "2026-10-18"),
            date("Meeting", "2026-10-19"),
            task("No date", null)
        )

        assertEquals(listOf("Overdue", "Trip", "Payment"), AgendaSchedule.thisWeek(items, today).map { it.label })
        assertEquals(listOf("Overdue", "Trip", "Payment", "Meeting"), AgendaSchedule.thisWeek(items, today, limit = 10).map { it.label })
        assertEquals(2, AgendaSchedule.dueSoonTaskCount(items, today))
    }

    @Test
    fun `items due within two days are urgent unless done`() {
        assertTrue(AgendaSchedule.isUrgent(task("Overdue", "2026-10-12"), today))
        assertTrue(AgendaSchedule.isUrgent(date("In two days", "2026-10-15"), today))
        assertFalse(AgendaSchedule.isUrgent(date("In three days", "2026-10-16"), today))
        assertFalse(AgendaSchedule.isUrgent(task("Done", "2026-10-13", done = true), today))
        assertFalse(AgendaSchedule.isUrgent(task("No date", null), today))
    }
}
