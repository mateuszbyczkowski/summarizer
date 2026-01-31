package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import com.summarizer.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    fun verifyPin(pin: String): Boolean {
        return authRepository.verifyPin(pin)
    }
}
