package com.summarizer.app.ui.screens.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ThreadListUiState {
    data object Loading : ThreadListUiState
    data class Success(val threads: List<Thread>) : ThreadListUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val threadRepository: ThreadRepository
) : ViewModel() {

    private val _showOnlyFollowed = MutableStateFlow(true)
    val showOnlyFollowed: StateFlow<Boolean> = _showOnlyFollowed

    // Room flows update live, so there is nothing to refresh manually
    val uiState: StateFlow<ThreadListUiState> = _showOnlyFollowed
        .flatMapLatest { onlyFollowed ->
            if (onlyFollowed) threadRepository.getFollowedThreads() else threadRepository.getAllThreads()
        }
        .map<List<Thread>, ThreadListUiState> { ThreadListUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThreadListUiState.Loading
        )

    fun toggleFilter() {
        _showOnlyFollowed.value = !_showOnlyFollowed.value
    }

    fun toggleFollowStatus(threadId: String, isFollowed: Boolean) {
        viewModelScope.launch {
            threadRepository.updateFollowStatus(threadId, isFollowed)
        }
    }
}
