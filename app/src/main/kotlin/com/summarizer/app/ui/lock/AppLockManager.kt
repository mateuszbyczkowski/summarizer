package com.summarizer.app.ui.lock

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.BiometricHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the app behind the phone's own lock (or the app PIN on phones without a screen lock)
 * without asking too often.
 *
 * An unlock stays valid until the app has been unused for [AppLockPolicy.UNLOCK_VALIDITY_MS].
 * The last-use time is persisted, so the unlock survives the system killing the process, while
 * a stale session still locks the restored screens. The "App lock" setting turns it off.
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    private val _state = MutableStateFlow<AppLockState>(AppLockState.Unknown)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    /**
     * Last use seen by this process. Covers an unlock that happens while [evaluate] is reading
     * the persisted time, which would otherwise lock the app again right after the unlock.
     */
    @Volatile
    private var lastActiveInProcess: Long? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** The app PIN was entered (or created). */
    fun unlock() {
        markActive()
        _state.value = AppLockState.Unlocked
        scope.launch { authRepository.setLastActiveAt(System.currentTimeMillis()) }
    }

    /**
     * The phone's own lock let the user in. A PIN from before the switch to the system lock
     * is dropped: it is no longer asked for, and would be forgotten by the time it was.
     */
    fun unlockWithSystemLock() {
        markActive()
        _state.value = AppLockState.Unlocked
        scope.launch {
            // clearPin() also clears the last-use time, so record it afterwards
            if (authRepository.hasPin()) authRepository.clearPin()
            authRepository.setLastActiveAt(System.currentTimeMillis())
        }
    }

    /** Call after the PIN was set or cleared; the user just proved knowledge of it (or has none). */
    fun onPinChanged() = unlock()

    /** Re-checks how the app is protected, e.g. after the screen lock turned out to be gone. */
    fun refresh() {
        scope.launch { evaluate() }
    }

    /** True when turning the app lock on needs an app PIN first (no screen lock, no PIN). */
    suspend fun needsPinSetup(): Boolean =
        !BiometricHelper.isDeviceSecure(context) && !authRepository.hasPin()

    override fun onStart(owner: LifecycleOwner) = refresh()

    override fun onStop(owner: LifecycleOwner) {
        if (_state.value == AppLockState.Unlocked) {
            markActive()
            scope.launch { authRepository.setLastActiveAt(System.currentTimeMillis()) }
        }
    }

    private suspend fun evaluate() {
        val method = AppLockPolicy.method(
            onboardingComplete = preferencesRepository.hasCompletedOnboarding(),
            appLockEnabled = preferencesRepository.isAppLockEnabled(),
            deviceSecure = BiometricHelper.isDeviceSecure(context),
            hasPin = authRepository.hasPin()
        )
        val persisted = authRepository.getLastActiveAt()
        _state.update { current ->
            val lastActiveAt = listOfNotNull(persisted, lastActiveInProcess).maxOrNull()
            val expired = AppLockPolicy.isExpired(lastActiveAt, System.currentTimeMillis())
            AppLockPolicy.nextState(current, method, expired)
        }
    }

    private fun markActive() {
        lastActiveInProcess = System.currentTimeMillis()
    }
}
