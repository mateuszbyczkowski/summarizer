package com.summarizer.app.ui.screens.onboarding

import com.summarizer.app.domain.model.AIProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun `onboarding has exactly three numbered steps`() {
        assertEquals(3, OnboardingStep.COUNT)
        assertEquals(listOf(1, 2, 3), OnboardingStep.entries.map { it.number })
    }

    @Test
    fun `a PIN is needed only without a screen lock`() {
        assertTrue(OnboardingFlow.needsPin(appLockEnabled = true, deviceSecure = false, hasPin = false))
        assertFalse(OnboardingFlow.needsPin(appLockEnabled = true, deviceSecure = true, hasPin = false))
        assertFalse(OnboardingFlow.needsPin(appLockEnabled = true, deviceSecure = false, hasPin = true))
        assertFalse(OnboardingFlow.needsPin(appLockEnabled = false, deviceSecure = false, hasPin = false))
    }

    @Test
    fun `on device goes straight to Home unless a PIN is needed`() {
        assertEquals(OnboardingNext.FINISH, OnboardingFlow.afterEngine(AIProvider.LOCAL, needsPin = false))
        assertEquals(OnboardingNext.PIN_SETUP, OnboardingFlow.afterEngine(AIProvider.LOCAL, needsPin = true))
    }

    @Test
    fun `OpenAI asks for the key first, then the PIN when needed`() {
        assertEquals(OnboardingNext.OPENAI_SETUP, OnboardingFlow.afterEngine(AIProvider.OPENAI, needsPin = true))
        assertEquals(OnboardingNext.OPENAI_SETUP, OnboardingFlow.afterEngine(AIProvider.OPENAI, needsPin = false))
        assertEquals(OnboardingNext.PIN_SETUP, OnboardingFlow.afterSetup(needsPin = true))
        assertEquals(OnboardingNext.FINISH, OnboardingFlow.afterSetup(needsPin = false))
    }

    @Test
    fun `notification access moves on by itself only when granted in the settings it opened`() {
        assertTrue(OnboardingFlow.autoAdvance(openedSettings = true, granted = true))
        assertFalse(OnboardingFlow.autoAdvance(openedSettings = true, granted = false))
        // Back from the engine step with access already on: Back must keep working
        assertFalse(OnboardingFlow.autoAdvance(openedSettings = false, granted = true))
    }
}
