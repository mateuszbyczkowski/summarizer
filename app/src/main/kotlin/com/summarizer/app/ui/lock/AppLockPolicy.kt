package com.summarizer.app.ui.lock

/** How the user proves they may open the app. */
enum class LockMethod {
    /** The phone's own screen lock (fingerprint, face, PIN, pattern) via BiometricPrompt. */
    SYSTEM,

    /** The app PIN; only on phones without a screen lock. */
    PIN,

    /** No screen lock and no app PIN yet: a PIN has to be created before the app opens. */
    PIN_SETUP
}

/** What the activity shows: nothing yet, the app, or the lock screen. */
sealed interface AppLockState {
    data object Unknown : AppLockState
    data object Unlocked : AppLockState
    data class Locked(val method: LockMethod) : AppLockState
}

/** Pure lock decisions, kept free of Android so they can be unit tested. */
object AppLockPolicy {

    const val UNLOCK_VALIDITY_MS = 24 * 60 * 60_000L

    /** The method that protects the app, or null when the app must not lock at all. */
    fun method(
        onboardingComplete: Boolean,
        appLockEnabled: Boolean,
        deviceSecure: Boolean,
        hasPin: Boolean
    ): LockMethod? = when {
        // Onboarding sets the lock up; locking before that would lock the user out of it
        !onboardingComplete || !appLockEnabled -> null
        deviceSecure -> LockMethod.SYSTEM
        hasPin -> LockMethod.PIN
        else -> LockMethod.PIN_SETUP
    }

    /** An unlock stays valid until the app has been unused for [UNLOCK_VALIDITY_MS]. */
    fun isExpired(lastActiveAt: Long?, now: Long): Boolean =
        lastActiveAt == null || now - lastActiveAt >= UNLOCK_VALIDITY_MS

    /**
     * The state after the app came to the foreground. Never unlocks a locked app (only a
     * successful authentication does), but follows a change of method, e.g. the screen lock
     * was removed while the lock screen was showing.
     */
    fun nextState(current: AppLockState, method: LockMethod?, expired: Boolean): AppLockState = when {
        current is AppLockState.Locked -> method?.let { AppLockState.Locked(it) } ?: current
        method != null && expired -> AppLockState.Locked(method)
        current is AppLockState.Unknown -> AppLockState.Unlocked
        else -> current
    }
}
