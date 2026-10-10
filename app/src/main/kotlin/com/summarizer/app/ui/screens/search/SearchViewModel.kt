package com.summarizer.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.local.preferences.RecentSearchesStore
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Search screen.
 *
 * Searches summaries, their tasks and messages (debounced; a newer query cancels a running
 * search), narrows the results with filter chips and remembers recent queries.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val messageRepository: MessageRepository,
    private val recentSearchesStore: RecentSearchesStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    /** Recent queries, newest first. */
    val recentSearches: StateFlow<List<String>> = recentSearchesStore.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // transformLatest cancels a running search when the query changes, so stale results
    // can never overwrite newer ones
    private val results = _searchQuery
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

    val uiState: StateFlow<SearchUiState> = combine(results, _filter) { state, filter ->
        if (state is SearchUiState.Success) state.filtered(filter) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Empty)

    fun updateSearchQuery(query: String) {
        if (query.trim() != _searchQuery.value.trim()) {
            // The chat chips belong to the previous results
            _filter.value = _filter.value.copy(threadId = null)
        }
        _searchQuery.value = query
    }

    fun clearSearch() {
        updateSearchQuery("")
    }

    /** The user pressed search on the keyboard: remember the query. */
    fun submitSearch(query: String) {
        rememberQuery(query)
    }

    /** Run a recent query again. */
    fun useRecentSearch(query: String) {
        updateSearchQuery(query)
        rememberQuery(query)
    }

    /** A result was opened: the query was useful, remember it. */
    fun onResultOpened() {
        rememberQuery(_searchQuery.value)
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { recentSearchesStore.remove(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchesStore.clear() }
    }

    /** Toggle a result-type chip; selecting the active one shows everything again. */
    fun toggleTypeFilter(type: SearchResultType) {
        _filter.value = _filter.value.let { it.copy(type = if (it.type == type) null else type) }
    }

    /** Toggle a chat chip. */
    fun toggleChatFilter(threadId: String) {
        _filter.value = _filter.value.let { it.copy(threadId = if (it.threadId == threadId) null else threadId) }
    }

    private fun rememberQuery(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch { recentSearchesStore.add(query) }
    }

    private suspend fun search(query: String): SearchUiState = try {
        val summaries = summaryRepository.searchSummaries(query)
        val messages = messageRepository.searchMessages(query)
        if (summaries.isEmpty() && messages.isEmpty()) {
            SearchUiState.NoResults(query)
        } else {
            SearchUiState.Success(
                query = query,
                summaries = summaries,
                messages = messages,
                tasks = SearchResultsLogic.taskHits(summaries, query),
                chats = SearchResultsLogic.chats(summaries, messages)
            )
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

    /**
     * @property chats every chat in the unfiltered results (chat chips appear when there are several)
     * @property filter the filter [summaries], [tasks] and [messages] are limited to
     */
    data class Success(
        val query: String,
        val summaries: List<Summary>,
        val messages: List<Message>,
        val tasks: List<TaskHit> = emptyList(),
        val chats: List<SearchChat> = emptyList(),
        val filter: SearchFilter = SearchFilter()
    ) : SearchUiState() {
        val isEmpty: Boolean get() = summaries.isEmpty() && tasks.isEmpty() && messages.isEmpty()

        fun filtered(filter: SearchFilter): Success {
            val (s, t, m) = SearchResultsLogic.apply(summaries, tasks, messages, filter)
            return copy(summaries = s, tasks = t, messages = m, filter = filter)
        }
    }

    data class NoResults(val query: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
