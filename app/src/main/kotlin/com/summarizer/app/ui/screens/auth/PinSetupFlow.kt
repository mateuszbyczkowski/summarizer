package com.summarizer.app.ui.screens.auth

import com.summarizer.app.util.Constants

/**
 * Enter-then-confirm PIN creation on the keypad, as a pure state machine.
 *
 * The first PIN moves to the confirm step as soon as it is complete; a confirmation that does
 * not match starts over with [mismatch] set. [completedPin] is set once both entries match.
 */
data class PinSetupFlow(
    val confirming: Boolean = false,
    val entry: String = "",
    val mismatch: Boolean = false,
    val completedPin: String? = null,
    private val firstPin: String = ""
) {
    fun onDigit(digit: Char): PinSetupFlow {
        if (completedPin != null || !digit.isDigit() || entry.length >= Constants.PIN_LENGTH) return this
        val next = entry + digit
        return when {
            next.length < Constants.PIN_LENGTH -> copy(entry = next, mismatch = false)
            !confirming -> PinSetupFlow(confirming = true, firstPin = next)
            next == firstPin -> copy(entry = next, completedPin = next)
            else -> PinSetupFlow(mismatch = true)
        }
    }

    fun onBackspace(): PinSetupFlow =
        if (completedPin != null || entry.isEmpty()) this else copy(entry = entry.dropLast(1))

    /** Saving failed: let the user confirm again. */
    fun onSaveFailed(): PinSetupFlow = copy(entry = "", completedPin = null)
}
