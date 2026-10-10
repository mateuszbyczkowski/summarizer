package com.summarizer.app.domain.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.summarizer.app.domain.model.ThreadSettings
import com.summarizer.app.util.ThreadNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Runs the regex-based parsers on Android, whose ICU regex engine rejects some patterns the
 * JVM accepts (e.g. `[:.]`, read as a POSIX class): the JVM unit tests cannot catch those, and
 * a pattern that fails to compile crashes the app as soon as a summary is shown.
 */
@RunWith(AndroidJUnit4::class)
class ParsersOnDeviceTest {

    @Test
    fun summaryParserReadsTimeRangesAndDropsChatter() {
        val parsed = SummaryResponseParser.parse(
            """
            OVERVIEW: Mecz w niedzielę.
            TOPICS:
            - Mecz
            ACTIONS:
            - Potwierdzić obecność (do 15.10)
            ANNOUNCEMENTS:
            - None
            DATES:
            - 18.10 19:30-21:00 | Mecz, Orlik
            - 19.10 od 19:30 do 21 | Trening
            - 20.10 19.30–21 | Basen
            - 10.10 14:05 | Anna odpowiedziała w wątku
            """.trimIndent(),
            reference = LocalDate.of(2026, 10, 10)
        )
        assertEquals(listOf("19:30", "19:30", "19:30"), parsed.keyDates.map { it.time })
        assertEquals(listOf("21:00", "21:00", "21:00"), parsed.keyDates.map { it.endTime })
        assertEquals("2026-10-15", parsed.actions.single().due)
    }

    @Test
    fun otherParsersCompileOnAndroid() {
        assertEquals("Answer", stripReasoning("<think>notes</think>Answer"))
        assertEquals(ThreadNaming.threadIdFor("Klasa 3B"), ThreadNaming.threadIdFor("Klasa 3B"))
        assertEquals("Parents of class 3B", ThreadSettings.normalizeDescription(" Parents of\nclass 3B "))
        assertTrue(SummaryResponseParser.streamingOverview("OVERVIEW: Mecz").startsWith("Mecz"))
    }
}
