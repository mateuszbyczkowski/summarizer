package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.PinVerification
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinLockUiState(
    val entry: String = "",
    val verifying: Boolean = false,
    /** Set after a wrong PIN: how many more wrong entries are allowed before a pause. */
    val attemptsBeforeLockout: Int? = null,
    /** Seconds until PINs are accepted again; 0 when not locked out. */
    val lockoutSeconds: Int = 0,
    /** Grows with every rejected entry; drives the shake and haptic feedback. */
    val rejections: Int = 0,
    val unlocked: Boolean = false
) {
    val keypadEnabled: Boolean get() = !verifying && lockoutSeconds == 0 && !unlocked
}

@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinLockUiState())
    val uiState: StateFlow<PinLockUiState> = _uiState.asStateFlow()

    private var countdown: Job? = null

    fun onDigit(digit: Char) {
        val state = _uiState.value
        if (!state.keypadEnabled || state.entry.length >= Constants.PIN_LENGTH) return
        val entry = state.entry + digit
        _uiState.value = state.copy(entry = entry, attemptsBeforeLockout = null)
        if (entry.length == Constants.PIN_LENGTH) verify(entry)
    }

    fun onBackspace() = _uiState.update {
        if (it.keypadEnabled) it.copy(entry = it.entry.dropLast(1)) else it
    }

    private fun verify(pin: String) {
        _uiState.update { it.copy(verifying = true) }
        viewModelScope.launch {
            val result = authRepository.verifyPin(pin)
            _uiState.update { state ->
                when (result) {
                    PinVerification.Success -> state.copy(verifying = false, unlocked = true)
                    is PinVerification.Incorrect -> state.copy(
                        entry = "",
                        verifying = false,
                        attemptsBeforeLockout = result.attemptsBeforeLockout,
                        rejections = state.rejections + 1
                    )
                    is PinVerification.LockedOut -> state.copy(
                        entry = "",
                        verifying = false,
                        rejections = state.rejections + 1
                    )
                }
            }
            if (result is PinVerification.LockedOut) startCountdown(result.remainingMillis)
        }
    }

    private fun startCountdown(remainingMillis: Long) {
        countdown?.cancel()
        val until = System.currentTimeMillis() + remainingMillis
        countdown = viewModelScope.launch {
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) break
                _uiState.update { it.copy(lockoutSeconds = ((left + 999) / 1000).toInt()) }
                delay((left % 1000).takeIf { it > 0 } ?: 1000)
            }
            _uiState.update { it.copy(lockoutSeconds = 0) }
        }
    }
}
