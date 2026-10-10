package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinSetupUiState(
    val flow: PinSetupFlow = PinSetupFlow(),
    val saveFailed: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinSetupUiState())
    val uiState: StateFlow<PinSetupUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        val current = _uiState.value
        if (current.flow.completedPin != null) return
        val flow = current.flow.onDigit(digit)
        _uiState.value = current.copy(flow = flow, saveFailed = false)
        flow.completedPin?.let(::save)
    }

    fun onBackspace() = _uiState.update { it.copy(flow = it.flow.onBackspace()) }

    /** Back from the confirm step: choose the PIN again. */
    fun restart() = _uiState.update { PinSetupUiState() }

    private fun save(pin: String) {
        viewModelScope.launch {
            authRepository.setPin(pin)
                .onSuccess {
                    appLockManager.onPinChanged()
                    _uiState.update { it.copy(saved = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(flow = it.flow.onSaveFailed(), saveFailed = true) }
                }
        }
    }
}
