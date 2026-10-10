package com.summarizer.app.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSetupFlowTest {

    private fun PinSetupFlow.type(digits: String) = digits.fold(this) { flow, digit -> flow.onDigit(digit) }

    @Test
    fun `complete first entry moves to confirm`() {
        val flow = PinSetupFlow().type("123456")
        assertTrue(flow.confirming)
        assertEquals("", flow.entry)
        assertNull(flow.completedPin)
    }

    @Test
    fun `matching confirmation completes`() {
        val flow = PinSetupFlow().type("123456").type("123456")
        assertEquals("123456", flow.completedPin)
    }

    @Test
    fun `mismatch starts over`() {
        val flow = PinSetupFlow().type("123456").type("654321")
        assertTrue(flow.mismatch)
        assertFalse(flow.confirming)
        assertEquals("", flow.entry)
        assertNull(flow.completedPin)
        // Typing again clears the error, and the old first PIN is forgotten
        val retried = flow.type("1")
        assertFalse(retried.mismatch)
        assertEquals("111111", retried.type("11111").type("111111").completedPin)
    }

    @Test
    fun `backspace edits the current entry only`() {
        val flow = PinSetupFlow().type("12").onBackspace()
        assertEquals("1", flow.entry)
        assertEquals("", PinSetupFlow().onBackspace().entry)
    }

    @Test
    fun `non digits and input after completion are ignored`() {
        assertEquals("", PinSetupFlow().onDigit('a').entry)
        val done = PinSetupFlow().type("123456").type("123456")
        assertEquals(done, done.onDigit('7'))
        assertEquals(done, done.onBackspace())
    }

    @Test
    fun `failed save asks for the confirmation again`() {
        val retry = PinSetupFlow().type("123456").type("123456").onSaveFailed()
        assertTrue(retry.confirming)
        assertNull(retry.completedPin)
        assertEquals("123456", retry.type("123456").completedPin)
    }
}
