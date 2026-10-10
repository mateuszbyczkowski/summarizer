package com.summarizer.app.ui.lock

import com.summarizer.app.ui.lock.AppLockPolicy.UNLOCK_VALIDITY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    private fun method(
        onboardingComplete: Boolean = true,
        appLockEnabled: Boolean = true,
        deviceSecure: Boolean = true,
        hasPin: Boolean = false
    ) = AppLockPolicy.method(onboardingComplete, appLockEnabled, deviceSecure, hasPin)

    @Test
    fun `secure phone uses the system lock, also for users who still have a PIN`() {
        assertEquals(LockMethod.SYSTEM, method(deviceSecure = true, hasPin = false))
        assertEquals(LockMethod.SYSTEM, method(deviceSecure = true, hasPin = true))
    }

    @Test
    fun `phone without screen lock uses the app PIN, or asks to create one`() {
        assertEquals(LockMethod.PIN, method(deviceSecure = false, hasPin = true))
        assertEquals(LockMethod.PIN_SETUP, method(deviceSecure = false, hasPin = false))
    }

    @Test
    fun `no lock when the setting is off or during onboarding`() {
        assertNull(method(appLockEnabled = false))
        assertNull(method(appLockEnabled = false, deviceSecure = false, hasPin = true))
        assertNull(method(onboardingComplete = false))
        assertNull(method(onboardingComplete = false, deviceSecure = false))
    }

    @Test
    fun `unlock expires after 24 hours without use`() {
        val now = 10 * UNLOCK_VALIDITY_MS
        assertTrue(AppLockPolicy.isExpired(null, now))
        assertFalse(AppLockPolicy.isExpired(now - UNLOCK_VALIDITY_MS + 1, now))
        assertTrue(AppLockPolicy.isExpired(now - UNLOCK_VALIDITY_MS, now))
    }

    @Test
    fun `first start locks only when expired`() {
        assertEquals(
            AppLockState.Locked(LockMethod.SYSTEM),
            AppLockPolicy.nextState(AppLockState.Unknown, LockMethod.SYSTEM, expired = true)
        )
        assertEquals(
            AppLockState.Unlocked,
            AppLockPolicy.nextState(AppLockState.Unknown, LockMethod.SYSTEM, expired = false)
        )
        assertEquals(
            AppLockState.Unlocked,
            AppLockPolicy.nextState(AppLockState.Unknown, null, expired = true)
        )
    }

    @Test
    fun `unlocked app locks again once expired`() {
        assertEquals(
            AppLockState.Locked(LockMethod.PIN),
            AppLockPolicy.nextState(AppLockState.Unlocked, LockMethod.PIN, expired = true)
        )
        assertEquals(
            AppLockState.Unlocked,
            AppLockPolicy.nextState(AppLockState.Unlocked, LockMethod.PIN, expired = false)
        )
        assertEquals(
            AppLockState.Unlocked,
            AppLockPolicy.nextState(AppLockState.Unlocked, null, expired = true)
        )
    }

    @Test
    fun `locked app never unlocks by itself but follows a method change`() {
        val locked = AppLockState.Locked(LockMethod.SYSTEM)
        assertEquals(locked, AppLockPolicy.nextState(locked, LockMethod.SYSTEM, expired = false))
        assertEquals(locked, AppLockPolicy.nextState(locked, null, expired = false))
        assertEquals(
            AppLockState.Locked(LockMethod.PIN_SETUP),
            AppLockPolicy.nextState(locked, LockMethod.PIN_SETUP, expired = false)
        )
    }
}
