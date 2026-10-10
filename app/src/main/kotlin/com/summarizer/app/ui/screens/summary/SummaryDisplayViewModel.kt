package com.summarizer.app.ui.screens.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SummaryUiState {
    data object Loading : SummaryUiState
    data object Initial : SummaryUiState
    data object Generating : SummaryUiState
    data class Success(val summary: Summary) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}

@HiltViewModel
class SummaryDisplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    summaryRepository: SummaryRepository,
    threadRepository: ThreadRepository,
    private val coordinator: SummaryGenerationCoordinator
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>("threadId") ?: ""

    val threadName: StateFlow<String> = flow {
        emit(threadRepository.getThread(threadId)?.threadName.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val uiState: StateFlow<SummaryUiState> = combine(
        summaryRepository.getSummariesForThread(threadId).map { it.firstOrNull() },
        coordinator.stateFor(threadId)
    ) { latest, generation ->
        when (generation) {
            SummaryGenerationCoordinator.State.Running -> SummaryUiState.Generating
            is SummaryGenerationCoordinator.State.Failed -> SummaryUiState.Error(generation.message)
            null -> latest?.let { SummaryUiState.Success(it) } ?: SummaryUiState.Initial
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)

    fun generateSummary() {
        coordinator.generate(threadId)
    }

    fun retry() {
        coordinator.generate(threadId)
    }

    fun dismissError() {
        viewModelScope.launch(Dispatchers.IO) { coordinator.dismissError(threadId) }
    }
}
