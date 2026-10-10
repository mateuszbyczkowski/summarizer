package com.summarizer.app.util

import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
import com.summarizer.app.domain.model.Summary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SummaryFormatterTest {

    private val labels = SummaryFormatter.Labels(
        dates = "Ważne daty",
        tasks = "Do zrobienia",
        announcements = "Ogłoszenia",
        topics = "Tematy",
        due = "do %1\$s",
        datePattern = "EEE d.MM"
    )

    @Test
    fun `summary becomes plain text with formatted dates`() {
        val summary = Summary(
            threadId = "t",
            threadName = "Klasa 3B",
            overview = "Wycieczka w piątek.",
            keyTopics = listOf("Wycieczka", "Zebranie"),
            actionItems = listOf(
                ActionItem("Przelew 85 zł", due = "2025-10-15"),
                ActionItem("Bluza", done = true)
            ),
            announcements = listOf("Basen odwołany"),
            participantHighlights = emptyList(),
            messageCount = 3,
            startTimestamp = 0,
            endTimestamp = 0,
            keyDates = listOf(
                KeyDate("Wycieczka", "2025-10-17", "07:30", "autobus"),
                KeyDate("Basen", null, null, "w środę")
            )
        )

        val text = SummaryFormatter.toPlainText(summary, labels, Locale.forLanguageTag("pl-PL"))

        assertEquals(
            """
            Klasa 3B
            Wycieczka w piątek.

            Ważne daty:
            • pt. 17.10, 07:30 – Wycieczka (autobus)
            • Basen (w środę)

            Do zrobienia:
            ☐ Przelew 85 zł (do śr. 15.10)
            ☑ Bluza

            Ogłoszenia:
            • Basen odwołany

            Tematy: Wycieczka · Zebranie
            """.trimIndent(),
            text
        )
    }
}
