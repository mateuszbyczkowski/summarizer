package com.summarizer.app.domain.model

/**
 * Outcome of a PIN entry attempt.
 */
sealed interface PinVerification {
    data object Success : PinVerification

    /** Wrong PIN; [attemptsBeforeLockout] more wrong entries are allowed before a lockout. */
    data class Incorrect(val attemptsBeforeLockout: Int) : PinVerification

    /** Too many wrong entries; no attempts are accepted for [remainingMillis]. */
    data class LockedOut(val remainingMillis: Long) : PinVerification
}
