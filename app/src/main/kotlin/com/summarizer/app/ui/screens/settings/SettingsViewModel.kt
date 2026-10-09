package com.summarizer.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.ai.OpenAIEngine
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.OpenAIModel
import com.summarizer.app.data.repository.AppDataResetter
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.ui.lock.AppLockManager
import com.summarizer.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Settings screen.
 *
 * Manages AI provider selection and OpenAI API key configuration.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val openAIEngine: OpenAIEngine,
    private val workScheduler: WorkScheduler,
    private val appDataResetter: AppDataResetter,
    private val appLockManager: AppLockManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Single entry point for Settings UI actions. Navigation events ([SettingsEvent.ManageModels],
     * [SettingsEvent.ResetApp]) need the nav controller and are handled by the screen instead.
     */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectProvider -> setAIProvider(event.provider)
            is SettingsEvent.SaveApiKey -> saveApiKey(event.apiKey)
            SettingsEvent.ValidateApiKey -> validateApiKey()
            SettingsEvent.ClearApiKey -> clearApiKey()
            is SettingsEvent.SelectOpenAIModel -> setSelectedModel(event.model)
            is SettingsEvent.SetAutoSummarizationEnabled -> setAutoSummarizationEnabled(event.enabled)
            is SettingsEvent.SetAutoSummarizationHour -> setAutoSummarizationHour(event.hour)
            is SettingsEvent.SetDataRetentionDays -> setDataRetentionDays(event.days)
            is SettingsEvent.SetBiometricEnabled -> setBiometricEnabled(event.enabled)
            is SettingsEvent.SetSmartNotificationsEnabled -> setSmartNotificationsEnabled(event.enabled)
            is SettingsEvent.SetSmartNotificationThreshold -> setSmartNotificationThreshold(event.threshold)
            SettingsEvent.ManageModels, SettingsEvent.ResetApp -> Unit
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val provider = preferencesRepository.getAIProvider()
                val apiKey = preferencesRepository.getOpenAIApiKey()
                val selectedModelId = preferencesRepository.getSelectedOpenAIModel()
                val selectedModel = OpenAIModel.fromModelId(selectedModelId)
                val autoSummarizationEnabled = preferencesRepository.isAutoSummarizationEnabled()
                val autoSummarizationHour = preferencesRepository.getAutoSummarizationHour()
                val dataRetentionDays = preferencesRepository.getDataRetentionDays()
                val biometricEnabled = preferencesRepository.isBiometricEnabled()
                val smartNotificationsEnabled = preferencesRepository.isSmartNotificationsEnabled()
                val smartNotificationThreshold = preferencesRepository.getSmartNotificationThreshold()

                _uiState.value = SettingsUiState.Success(
                    aiProvider = provider,
                    hasApiKey = !apiKey.isNullOrBlank(),
                    apiKeyMasked = apiKey?.let { "*".repeat(20) } ?: "",
                    selectedModel = selectedModel,
                    autoSummarizationEnabled = autoSummarizationEnabled,
                    autoSummarizationHour = autoSummarizationHour,
                    dataRetentionDays = dataRetentionDays,
                    biometricEnabled = biometricEnabled,
                    smartNotificationsEnabled = smartNotificationsEnabled,
                    smartNotificationThreshold = smartNotificationThreshold
                )

                Timber.d("Settings loaded: provider=$provider, hasApiKey=${!apiKey.isNullOrBlank()}, autoSummary=$autoSummarizationEnabled@$autoSummarizationHour, biometric=$biometricEnabled, smartNotifications=$smartNotificationsEnabled@$smartNotificationThreshold")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load settings")
                _uiState.value = SettingsUiState.Error("Failed to load settings: ${e.message}")
            }
        }
    }

    fun setAIProvider(provider: AIProvider) {
        viewModelScope.launch {
            try {
                Timber.d("Changing AI provider to: $provider")
                preferencesRepository.setAIProvider(provider)
                // Network is only required for OpenAI runs
                workScheduler.refreshAutoSummarization()

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set AI provider")
                _uiState.value = SettingsUiState.Error("Failed to change provider: ${e.message}")
            }
        }
    }

    fun saveApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            _uiState.value = SettingsUiState.Error("API key cannot be empty")
            return
        }

        viewModelScope.launch {
            try {
                Timber.d("Saving OpenAI API key (${apiKey.length} chars)")
                preferencesRepository.setOpenAIApiKey(apiKey.trim())

                // Reload settings to update UI
                loadSettings()

                Timber.i("API key saved successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save API key")
                _uiState.value = SettingsUiState.Error("Failed to save API key: ${e.message}")
            }
        }
    }

    fun validateApiKey() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is SettingsUiState.Success) {
                return@launch
            }

            _uiState.value = currentState.copy(isValidating = true, validationMessage = null)

            try {
                Timber.d("Validating OpenAI API key...")

                // Test API call with minimal tokens
                val result = openAIEngine.generate(
                    prompt = "Say 'OK'",
                    maxTokens = 5,
                    temperature = 0.0f
                )

                if (result.isSuccess) {
                    Timber.i("API key validation successful")
                    _uiState.value = currentState.copy(
                        isValidating = false,
                        validationMessage = "✓ API key is valid"
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Timber.w("API key validation failed: $error")
                    _uiState.value = currentState.copy(
                        isValidating = false,
                        validationMessage = "✗ Validation failed: $error"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "API key validation failed")
                _uiState.value = currentState.copy(
                    isValidating = false,
                    validationMessage = "✗ Validation failed: ${e.message}"
                )
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            try {
                Timber.d("Clearing OpenAI API key")
                preferencesRepository.clearOpenAIApiKey()

                // Switch back to local provider
                preferencesRepository.setAIProvider(AIProvider.LOCAL)
                // Network is no longer required for scheduled runs
                workScheduler.refreshAutoSummarization()

                // Reload settings to update UI
                loadSettings()

                Timber.i("API key cleared, switched to Local provider")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear API key")
                _uiState.value = SettingsUiState.Error("Failed to clear API key: ${e.message}")
            }
        }
    }

    fun dismissError() {
        loadSettings()
    }

    fun setAutoSummarizationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                Timber.d("Setting auto-summarization enabled: $enabled")
                preferencesRepository.setAutoSummarizationEnabled(enabled)
                workScheduler.refreshAutoSummarization()

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set auto-summarization enabled")
                _uiState.value = SettingsUiState.Error("Failed to update setting: ${e.message}")
            }
        }
    }

    fun setAutoSummarizationHour(hour: Int) {
        viewModelScope.launch {
            try {
                Timber.d("Setting auto-summarization hour: $hour")
                preferencesRepository.setAutoSummarizationHour(hour)

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set auto-summarization hour")
                _uiState.value = SettingsUiState.Error("Failed to update setting: ${e.message}")
            }
        }
    }

    fun setDataRetentionDays(days: Int) {
        viewModelScope.launch {
            try {
                Timber.d("Setting data retention days: $days")
                preferencesRepository.setDataRetentionDays(days)

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set data retention days")
                _uiState.value = SettingsUiState.Error("Failed to update setting: ${e.message}")
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                Timber.d("Setting biometric enabled: $enabled")
                preferencesRepository.setBiometricEnabled(enabled)

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set biometric enabled")
                _uiState.value = SettingsUiState.Error("Failed to update setting: ${e.message}")
            }
        }
    }

    fun setSmartNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                Timber.d("Setting smart notifications enabled: $enabled")
                preferencesRepository.setSmartNotificationsEnabled(enabled)

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set smart notifications enabled")
                _uiState.value = SettingsUiState.Error("Failed to update setting: ${e.message}")
            }
        }
    }

    fun setSmartNotificationThreshold(threshold: Float) {
        viewModelScope.launch {
            try {
                Timber.d("Setting smart notification threshold: $threshold")
                preferencesRepository.setSmartNotificationThreshold(threshold)

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set smart notification threshold")
                _uiState.value = SettingsUiState.Error("Failed to update setting: ${e.message}")
            }
        }
    }

    fun setSelectedModel(model: OpenAIModel) {
        viewModelScope.launch {
            try {
                Timber.d("Setting selected OpenAI model: ${model.modelId}")
                preferencesRepository.setSelectedOpenAIModel(model.modelId)

                // Reload settings to update UI
                loadSettings()
            } catch (e: Exception) {
                Timber.e(e, "Failed to set selected model")
                _uiState.value = SettingsUiState.Error("Failed to update model: ${e.message}")
            }
        }
    }

    /**
     * Wipe all app data. Runs in the application scope so leaving the screen cannot interrupt
     * it; [onComplete] is called only after everything was deleted.
     */
    fun resetApplication(onComplete: () -> Unit) {
        if (_isResetting.value) return
        _isResetting.value = true
        val reset = applicationScope.async { appDataResetter.resetAll() }
        viewModelScope.launch {
            try {
                reset.await()
                appLockManager.onPinChanged()
                Timber.i("Application reset completed successfully")
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset application")
                _uiState.value = SettingsUiState.Error("Failed to reset application: ${e.message}")
            } finally {
                _isResetting.value = false
            }
        }
    }
}

/**
 * UI state for Settings screen.
 */
sealed class SettingsUiState {
    data object Loading : SettingsUiState()

    data class Success(
        val aiProvider: AIProvider,
        val hasApiKey: Boolean,
        val apiKeyMasked: String,
        val selectedModel: OpenAIModel = OpenAIModel.DEFAULT,
        val isValidating: Boolean = false,
        val validationMessage: String? = null,
        val autoSummarizationEnabled: Boolean = false,
        val autoSummarizationHour: Int = 20,
        val dataRetentionDays: Int = 30,
        val biometricEnabled: Boolean = false,
        val smartNotificationsEnabled: Boolean = false,
        val smartNotificationThreshold: Float = 0.6f
    ) : SettingsUiState()

    data class Error(val message: String) : SettingsUiState()
}
