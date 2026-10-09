package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    fun setPin(pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            authRepository.setPin(pin)
                .onSuccess {
                    appLockManager.onPinChanged()
                    onSuccess()
                }
                .onFailure { onError(it.message ?: "Could not save PIN") }
        }
    }
}
