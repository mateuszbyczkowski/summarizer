package com.summarizer.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.SummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Search screen.
 *
 * Handles searching across both summaries and messages.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Set up debounced search
        viewModelScope.launch {
            _searchQuery
                .debounce(500) // Wait 500ms after user stops typing
                .collect { query ->
                    if (query.isNotBlank()) {
                        performSearch(query)
                    } else {
                        _uiState.value = SearchUiState.Empty
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                Timber.d("Performing search for: $query")
                _uiState.value = SearchUiState.Loading

                // Search both summaries and messages
                val summaries = summaryRepository.searchSummaries(query)
                val messages = messageRepository.searchMessages(query)

                Timber.d("Search results: ${summaries.size} summaries, ${messages.size} messages")

                if (summaries.isEmpty() && messages.isEmpty()) {
                    _uiState.value = SearchUiState.NoResults(query)
                } else {
                    _uiState.value = SearchUiState.Success(
                        query = query,
                        summaries = summaries,
                        messages = messages
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                _uiState.value = SearchUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.value = SearchUiState.Empty
    }
}

/**
 * UI state for Search screen.
 */
sealed class SearchUiState {
    data object Empty : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(
        val query: String,
        val summaries: List<Summary>,
        val messages: List<Message>
    ) : SearchUiState()
    data class NoResults(val query: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
