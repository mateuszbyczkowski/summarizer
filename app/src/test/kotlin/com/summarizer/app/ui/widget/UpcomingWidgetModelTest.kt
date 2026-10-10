package com.summarizer.app.ui.widget

import com.summarizer.app.domain.model.AgendaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpcomingWidgetModelTest {

    private val today = LocalDate.of(2026, 10, 13)

    private fun item(
        label: String,
        date: LocalDate?,
        kind: AgendaItem.Kind = AgendaItem.Kind.DATE,
        done: Boolean = false,
        time: String? = null,
        thread: String = "Class 3B"
    ) = AgendaItem(
        threadId = "id-$thread",
        threadName = thread,
        summaryId = 1,
        kind = kind,
        label = label,
        date = date?.toString(),
        time = time,
        index = 0,
        done = done
    )

    @Test
    fun `app lock on shows only the count of this week's items`() {
        val agenda = listOf(
            item("Trip", today.plusDays(1)),
            item("Pay", today.plusDays(2), AgendaItem.Kind.TASK),
            item("Later", today.plusDays(10))
        )

        val state = UpcomingWidgetModel.build(agenda, today, appLockEnabled = true)

        assertEquals(UpcomingWidgetState.CountOnly(2), state)
    }

    @Test
    fun `app lock on with nothing coming up counts zero`() {
        val state = UpcomingWidgetModel.build(emptyList(), today, appLockEnabled = true)

        assertEquals(UpcomingWidgetState.CountOnly(0), state)
    }

    @Test
    fun `details follow the this-week rules and keep agenda order`() {
        val agenda = listOf(
            item("Overdue task", today.minusDays(3), AgendaItem.Kind.TASK),
            item("Past date", today.minusDays(1)),
            item("Done task", today.plusDays(1), AgendaItem.Kind.TASK, done = true),
            item("Today", today, time = "07:30"),
            item("In six days", today.plusDays(6)),
            item("In a week", today.plusDays(7)),
            item("No date task", null, AgendaItem.Kind.TASK)
        )

        val state = UpcomingWidgetModel.build(agenda, today, appLockEnabled = false) as UpcomingWidgetState.Details

        assertEquals(listOf("Overdue task", "Today", "In six days"), state.rows.map { it.label })
        assertEquals(3, state.total)
        assertEquals("07:30", state.rows[1].time)
        assertEquals("id-Class 3B", state.rows[1].threadId)
    }

    @Test
    fun `details show at most four rows but count all`() {
        val agenda = (0 until 6).map { item("Item $it", today.plusDays(it.toLong())) }

        val state = UpcomingWidgetModel.build(agenda, today, appLockEnabled = false) as UpcomingWidgetState.Details

        assertEquals(UpcomingWidgetModel.MAX_ROWS, state.rows.size)
        assertEquals(6, state.total)
    }

    @Test
    fun `rows within two days or overdue are urgent`() {
        val agenda = listOf(
            item("Overdue", today.minusDays(1), AgendaItem.Kind.TASK),
            item("In two days", today.plusDays(2)),
            item("In three days", today.plusDays(3))
        )

        val rows = (UpcomingWidgetModel.build(agenda, today, appLockEnabled = false) as UpcomingWidgetState.Details).rows

        assertTrue(rows[0].urgent)
        assertTrue(rows[1].urgent)
        assertFalse(rows[2].urgent)
    }

    @Test
    fun `nothing coming up gives empty details`() {
        val state = UpcomingWidgetModel.build(listOf(item("Later", today.plusDays(9))), today, appLockEnabled = false)

        assertEquals(UpcomingWidgetState.Details(rows = emptyList(), total = 0), state)
    }

    @Test
    fun `rows that fit follow the height between one and four`() {
        assertEquals(1, UpcomingWidgetModel.rowsThatFit(60f))
        assertEquals(1, UpcomingWidgetModel.rowsThatFit(110f))
        assertEquals(1, UpcomingWidgetModel.rowsThatFit(140f))
        assertEquals(2, UpcomingWidgetModel.rowsThatFit(160f))
        assertEquals(3, UpcomingWidgetModel.rowsThatFit(200f))
        assertEquals(4, UpcomingWidgetModel.rowsThatFit(250f))
        assertEquals(4, UpcomingWidgetModel.rowsThatFit(600f))
    }
}
