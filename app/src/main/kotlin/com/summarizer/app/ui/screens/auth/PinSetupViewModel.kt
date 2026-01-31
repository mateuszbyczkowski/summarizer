package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import com.summarizer.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    fun setPin(pin: String) {
        authRepository.setPin(pin)
    }
}
