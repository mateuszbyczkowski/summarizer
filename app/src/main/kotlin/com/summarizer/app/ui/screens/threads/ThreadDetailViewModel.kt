package com.summarizer.app.ui.screens.threads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ThreadDetailUiState {
    object Loading : ThreadDetailUiState
    data class Success(val messages: List<Message>) : ThreadDetailUiState
    data class Error(val message: String) : ThreadDetailUiState
}

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageRepository: MessageRepository
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>("threadId") ?: ""
    private val _isLoading = MutableStateFlow(true)

    val messages: StateFlow<List<Message>> = messageRepository
        .getMessagesForThread(threadId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<ThreadDetailUiState> = combine(
        messages,
        _isLoading
    ) { messageList, isLoading ->
        when {
            isLoading && messageList.isEmpty() -> ThreadDetailUiState.Loading
            else -> {
                _isLoading.value = false
                ThreadDetailUiState.Success(messageList)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThreadDetailUiState.Loading
    )
}
