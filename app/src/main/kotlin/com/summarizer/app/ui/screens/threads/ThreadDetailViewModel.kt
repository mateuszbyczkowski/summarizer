package com.summarizer.app.ui.screens.threads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.domain.model.ThreadSettings
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ThreadDetailUiState {
    data object Loading : ThreadDetailUiState
    data class Success(val messages: List<Message>) : ThreadDetailUiState
    data class Error(val message: String) : ThreadDetailUiState
}

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageRepository: MessageRepository,
    private val threadRepository: ThreadRepository,
    private val threadSettingsRepository: ThreadSettingsRepository
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>("threadId") ?: ""

    val thread: StateFlow<Thread?> = threadRepository
        .observeThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val threadSettings: StateFlow<ThreadSettings?> = threadSettingsRepository
        .getSettingsFlow(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Loading only until Room's first emission; an empty thread shows the empty state
    val uiState: StateFlow<ThreadDetailUiState> = messageRepository
        .getMessagesForThread(threadId)
        .map<List<Message>, ThreadDetailUiState> { ThreadDetailUiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThreadDetailUiState.Loading)

    fun toggleFollowStatus(isFollowed: Boolean) {
        viewModelScope.launch {
            threadRepository.updateFollowStatus(threadId, isFollowed)
        }
    }

    fun updateSummarizationMode(mode: SummarizationMode) {
        viewModelScope.launch {
            // Get or create settings first
            val settings = threadSettingsRepository.getOrCreateSettings(threadId)
            // Update the mode
            threadSettingsRepository.saveSettings(
                settings.copy(
                    summarizationMode = mode,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
