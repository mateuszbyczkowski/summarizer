package com.summarizer.app.ui.screens.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ThreadListUiState {
    object Loading : ThreadListUiState
    data class Success(val threads: List<Thread>) : ThreadListUiState
    data class Error(val message: String) : ThreadListUiState
}

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    threadRepository: ThreadRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    val threads: StateFlow<List<Thread>> = threadRepository
        .getAllThreads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<ThreadListUiState> = combine(
        threads,
        _isLoading
    ) { threadList, isLoading ->
        when {
            isLoading && threadList.isEmpty() -> ThreadListUiState.Loading
            else -> {
                _isLoading.value = false
                ThreadListUiState.Success(threadList)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThreadListUiState.Loading
    )
}
