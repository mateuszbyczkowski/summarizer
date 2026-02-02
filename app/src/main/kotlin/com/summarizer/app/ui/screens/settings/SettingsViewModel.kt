package com.summarizer.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.ai.OpenAIEngine
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val openAIEngine: OpenAIEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val provider = preferencesRepository.getAIProvider()
                val apiKey = preferencesRepository.getOpenAIApiKey()

                _uiState.value = SettingsUiState.Success(
                    aiProvider = provider,
                    hasApiKey = !apiKey.isNullOrBlank(),
                    apiKeyMasked = apiKey?.let { "*".repeat(20) } ?: ""
                )

                Timber.d("Settings loaded: provider=$provider, hasApiKey=${!apiKey.isNullOrBlank()}")
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
        val isValidating: Boolean = false,
        val validationMessage: String? = null
    ) : SettingsUiState()

    data class Error(val message: String) : SettingsUiState()
}
