package com.summarizer.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadSettingsTest {

    @Test
    fun `a description is kept on one trimmed line`() {
        assertEquals(
            "Parents of class 3B: trips, payments",
            ThreadSettings.normalizeDescription("  Parents of class 3B:\n trips,\tpayments  ")
        )
    }

    @Test
    fun `a blank description is removed`() {
        assertNull(ThreadSettings.normalizeDescription("  \n "))
        assertNull(ThreadSettings.normalizeDescription(null))
    }

    @Test
    fun `a long description is shortened`() {
        val text = ThreadSettings.normalizeDescription("a".repeat(400))!!
        assertEquals(ThreadSettings.MAX_DESCRIPTION_LENGTH, text.length)
    }
}
