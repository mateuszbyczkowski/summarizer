package com.summarizer.app.ui.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the app behind the PIN without asking for it too often.
 *
 * An unlock stays valid until the app has been unused for [UNLOCK_VALIDITY_MS]. The last-use
 * time is persisted, so the unlock survives the system killing the process, while a stale
 * session still locks the restored screens.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    companion object {
        const val UNLOCK_VALIDITY_MS = 24 * 60 * 60_000L
    }

    /** null until the PIN state is known; true while the lock screen must be shown. */
    private val _isLocked = MutableStateFlow<Boolean?>(null)
    val isLocked: StateFlow<Boolean?> = _isLocked.asStateFlow()

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun unlock() {
        _isLocked.value = false
        recordActivity()
    }

    /** Call after the PIN was set or cleared; the user just proved knowledge of it (or has none). */
    fun onPinChanged() = unlock()

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val lastActiveAt = authRepository.getLastActiveAt()
            val expired = lastActiveAt == null ||
                System.currentTimeMillis() - lastActiveAt >= UNLOCK_VALIDITY_MS
            val lock = authRepository.hasPin() && expired
            // Never auto-unlock here; only a correct PIN unlocks
            if (lock || _isLocked.value == null) _isLocked.value = lock
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_isLocked.value == false) recordActivity()
    }

    private fun recordActivity() {
        scope.launch { authRepository.setLastActiveAt(System.currentTimeMillis()) }
    }
}
