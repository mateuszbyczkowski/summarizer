package com.summarizer.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    fun setPin(pin: String) {
        authRepository.setPin(pin)
        // Mark first launch and onboarding as complete
        viewModelScope.launch {
            preferencesRepository.setFirstLaunchComplete()
            preferencesRepository.setOnboardingComplete()
        }
    }
}
