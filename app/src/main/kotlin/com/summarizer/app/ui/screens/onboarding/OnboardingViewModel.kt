package com.summarizer.app.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.download.ModelSetupManager
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.BiometricHelper
import com.summarizer.app.util.DeviceMemory
import com.summarizer.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The engine step and what follows it: starts the background model download when the user
 * keeps "On this phone", and decides whether an app PIN is needed before Home.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val workScheduler: WorkScheduler,
    private val modelSetupManager: ModelSetupManager
) : ViewModel() {

    /** The model "On this phone" will download, picked by RAM; null when none fits. */
    val recommendedModel: AIModel? = modelSetupManager.recommendedModel()

    val deviceRamGb: Int = DeviceMemory.totalRamGb(context)

    private val _next = MutableStateFlow<OnboardingNext?>(null)
    /** Where to go next; reset with [onNavigated] once the screen has navigated. */
    val next: StateFlow<OnboardingNext?> = _next.asStateFlow()

    private var busy = false

    /** The user confirmed the engine step with [provider]. */
    fun confirmEngine(provider: AIProvider) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                if (provider == AIProvider.LOCAL) {
                    preferencesRepository.setAIProvider(AIProvider.LOCAL)
                    workScheduler.refreshAutoSummarization()
                    modelSetupManager.start()
                }
                // OpenAI becomes the engine once its key is saved
                _next.value = OnboardingFlow.afterEngine(provider, needsPin())
            } finally {
                busy = false
            }
        }
    }

    /** The OpenAI key was saved, or the user skipped it. */
    fun openAiSetupDone() {
        viewModelScope.launch { _next.value = OnboardingFlow.afterSetup(needsPin()) }
    }

    fun onNavigated() {
        _next.value = null
    }

    private suspend fun needsPin(): Boolean = OnboardingFlow.needsPin(
        appLockEnabled = preferencesRepository.isAppLockEnabled(),
        deviceSecure = BiometricHelper.isDeviceSecure(context),
        hasPin = authRepository.hasPin()
    )
}
