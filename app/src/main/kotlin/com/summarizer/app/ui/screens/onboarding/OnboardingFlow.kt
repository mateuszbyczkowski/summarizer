package com.summarizer.app.ui.screens.onboarding

import com.summarizer.app.domain.model.AIProvider

/** The three numbered onboarding steps; the step indicator shows [number] of [COUNT]. */
enum class OnboardingStep(val number: Int) {
    WELCOME(1),
    NOTIFICATION_ACCESS(2),
    ENGINE(3);

    companion object {
        val COUNT = entries.size
    }
}

/** Where onboarding goes after the engine step. */
enum class OnboardingNext {
    /** Enter the OpenAI API key (part of the engine step, not a step of its own). */
    OPENAI_SETUP,

    /** Create an app PIN: only on phones without a screen lock. Not counted as a step. */
    PIN_SETUP,

    /** Done: Home, while the model downloads in the background. */
    FINISH
}

/** Pure onboarding decisions, kept free of Android so they can be unit tested. */
object OnboardingFlow {

    /**
     * An app PIN is asked for only when the app lock is on and the phone has no screen lock
     * (and no PIN exists yet); otherwise the app lock uses the phone's own lock.
     */
    fun needsPin(appLockEnabled: Boolean, deviceSecure: Boolean, hasPin: Boolean): Boolean =
        appLockEnabled && !deviceSecure && !hasPin

    fun afterEngine(provider: AIProvider, needsPin: Boolean): OnboardingNext = when (provider) {
        AIProvider.OPENAI -> OnboardingNext.OPENAI_SETUP
        AIProvider.LOCAL -> afterSetup(needsPin)
    }

    /** After the OpenAI key was saved (or skipped). */
    fun afterSetup(needsPin: Boolean): OnboardingNext =
        if (needsPin) OnboardingNext.PIN_SETUP else OnboardingNext.FINISH

    /**
     * The notification access step moves on by itself only when the user comes back from the
     * system settings it opened with access turned on. Otherwise (access already on, e.g. back
     * from the engine step) it waits for Continue, so Back still works.
     */
    fun autoAdvance(openedSettings: Boolean, granted: Boolean): Boolean = openedSettings && granted
}
