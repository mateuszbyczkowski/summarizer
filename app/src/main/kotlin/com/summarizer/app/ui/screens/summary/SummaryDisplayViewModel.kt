package com.summarizer.app.ui.screens.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.usecase.GenerateSummaryUseCase
import com.summarizer.app.domain.repository.SummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SummaryUiState {
    data object Initial : SummaryUiState
    data object Generating : SummaryUiState
    data class Success(val summary: Summary) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}

@HiltViewModel
class SummaryDisplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    private val summaryRepository: SummaryRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>("threadId") ?: ""

    private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Initial)
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        loadExistingSummary()
    }

    /**
     * Load the most recent summary for this thread if it exists.
     */
    private fun loadExistingSummary() {
        viewModelScope.launch {
            try {
                val existingSummary = summaryRepository.getLatestSummaryForThread(threadId)
                if (existingSummary != null) {
                    _uiState.update { SummaryUiState.Success(existingSummary) }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load existing summary")
                // Don't show error for missing summary, just stay in Initial state
            }
        }
    }

    /**
     * Generate a new summary for the thread.
     * Uses application scope so generation continues even if user navigates away.
     */
    fun generateSummary() {
        // Update UI state immediately
        _uiState.update { SummaryUiState.Generating }

        // Launch in application scope so it survives navigation
        applicationScope.launch {
            try {
                Timber.i("Starting summary generation for thread: $threadId")

                val result = generateSummaryUseCase.execute(threadId)

                result.onSuccess { summary ->
                    Timber.i("Summary generated successfully")
                    _uiState.update { SummaryUiState.Success(summary) }
                }.onFailure { error ->
                    Timber.e(error, "Failed to generate summary")
                    val errorMessage = when (error) {
                        is IllegalArgumentException -> "Thread not found"
                        is IllegalStateException -> error.message ?: "Invalid state"
                        else -> "Failed to generate summary: ${error.message}"
                    }
                    _uiState.update { SummaryUiState.Error(errorMessage) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during summary generation")
                _uiState.update {
                    SummaryUiState.Error("Unexpected error: ${e.message}")
                }
            }
        }
    }

    /**
     * Retry summary generation after an error.
     */
    fun retry() {
        generateSummary()
    }

    /**
     * Reset to initial state.
     */
    fun reset() {
        _uiState.update { SummaryUiState.Initial }
    }
}
