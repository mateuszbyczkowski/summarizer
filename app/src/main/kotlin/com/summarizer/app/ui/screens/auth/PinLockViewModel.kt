package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    init {
        loadBiometricPreference()
    }

    private fun loadBiometricPreference() {
        viewModelScope.launch {
            _isBiometricEnabled.value = preferencesRepository.isBiometricEnabled()
        }
    }

    fun verifyPin(pin: String): Boolean {
        return authRepository.verifyPin(pin)
    }
}
