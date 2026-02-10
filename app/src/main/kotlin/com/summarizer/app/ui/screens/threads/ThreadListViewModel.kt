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
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ThreadListUiState {
    data object Loading : ThreadListUiState
    data class Success(val threads: List<Thread>) : ThreadListUiState
    data class Error(val message: String) : ThreadListUiState
}

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val threadRepository: ThreadRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _showOnlyFollowed = MutableStateFlow(true)
    val showOnlyFollowed: StateFlow<Boolean> = _showOnlyFollowed

    init {
        // Merge any duplicate threads on startup
        viewModelScope.launch {
            threadRepository.mergeDuplicateThreads()
            // Set loading to false after initial load
            kotlinx.coroutines.delay(500)
            _isLoading.value = false
        }
    }

    val threads: StateFlow<List<Thread>> = combine(
        _showOnlyFollowed,
        threadRepository.getAllThreads(),
        threadRepository.getFollowedThreads()
    ) { showOnlyFollowed, allThreads, followedThreads ->
        if (showOnlyFollowed) followedThreads else allThreads
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<ThreadListUiState> = combine(
        threads,
        _isLoading
    ) { threadList, isLoading ->
        when {
            isLoading -> ThreadListUiState.Loading
            else -> ThreadListUiState.Success(threadList)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThreadListUiState.Loading
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // The Flow automatically refreshes, so just reset the loading state
            kotlinx.coroutines.delay(500) // Small delay for visual feedback
            _isRefreshing.value = false
        }
    }

    fun toggleFilter() {
        _showOnlyFollowed.value = !_showOnlyFollowed.value
    }

    fun toggleFollowStatus(threadId: String, isFollowed: Boolean) {
        viewModelScope.launch {
            threadRepository.updateFollowStatus(threadId, isFollowed)
        }
    }
}
