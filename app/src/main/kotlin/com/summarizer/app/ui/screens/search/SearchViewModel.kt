package com.summarizer.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.SummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Search screen.
 *
 * Handles searching across both summaries and messages.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // transformLatest cancels a running search when the query changes, so stale results
    // can never overwrite newer ones
    val uiState: StateFlow<SearchUiState> = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .transformLatest { query ->
            if (query.isEmpty()) {
                emit(SearchUiState.Empty)
                return@transformLatest
            }
            emit(SearchUiState.Loading)
            emit(search(query))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Empty)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    private suspend fun search(query: String): SearchUiState = try {
        val summaries = summaryRepository.searchSummaries(query)
        val messages = messageRepository.searchMessages(query)
        if (summaries.isEmpty() && messages.isEmpty()) {
            SearchUiState.NoResults(query)
        } else {
            SearchUiState.Success(query = query, summaries = summaries, messages = messages)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Search failed")
        SearchUiState.Error("Search failed: ${e.message}")
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
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
