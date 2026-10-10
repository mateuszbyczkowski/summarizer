package com.summarizer.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.download.ModelSetupManager
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.model.ModelSetupState
import com.summarizer.app.domain.model.ThreadOverview
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.AgendaSchedule
import com.summarizer.app.domain.usecase.ObserveAgendaUseCase
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import com.summarizer.app.util.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A thread on the home screen with its summary generation, if one is queued or running. */
data class ThreadRowState(
    val overview: ThreadOverview,
    val generation: SummaryGenerationCoordinator.State?
) {
    val threadId: String get() = overview.thread.threadId
    val isGenerating: Boolean get() = generation is SummaryGenerationCoordinator.State.Running

    /** The last attempt failed (not merely "nothing new to summarize") and was not dismissed. */
    val hasFailed: Boolean
        get() = (generation as? SummaryGenerationCoordinator.State.Failed)?.nothingNew == false
}

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * @property now Current time (epoch millis), refreshed every minute for relative times
     * @property thisWeek The next agenda items for the "This week" card
     */
    data class Ready(
        val followed: List<ThreadRowState>,
        val others: List<ThreadRowState>,
        val thisWeek: List<AgendaItem>,
        val now: Long,
        val today: LocalDate
    ) : HomeUiState {
        val hasThreads: Boolean get() = followed.isNotEmpty() || others.isNotEmpty()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val coordinator: SummaryGenerationCoordinator,
    private val modelSetupManager: ModelSetupManager,
    private val modelRepository: ModelRepository
) : ViewModel() {

    /** The model onboarding is downloading, for the progress card; null when there is none. */
    val modelSetup: StateFlow<ModelSetupState?> = modelSetupManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val engineConfigured = MutableStateFlow(true)

    /** No engine can summarize and no model is on its way: Home points to the AI engine screen. */
    val engineMissing: StateFlow<Boolean> = combine(engineConfigured, modelSetup) { configured, setup ->
        !configured && setup == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Re-checks the engine; the user may have set one up while away from Home. */
    fun refreshEngine() {
        viewModelScope.launch {
            engineConfigured.value = when (preferencesRepository.getAIProvider()) {
                AIProvider.LOCAL -> modelRepository.getDownloadedModel() != null
                AIProvider.OPENAI -> !preferencesRepository.getOpenAIApiKey().isNullOrBlank()
            }
        }
    }

    fun retryModelSetup() = modelSetupManager.retry()

    fun cancelModelSetup() = modelSetupManager.cancel()

    private val overviews = threadRepository.getThreadOverviews()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** Generation state of every thread that has one, by thread ID (one WorkManager query). */
    private val generations = coordinator.runningStates()

    val uiState: StateFlow<HomeUiState> = combine(overviews, generations, minuteTicks()) { list, states, now ->
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val rows = list.map { ThreadRowState(it, states[it.thread.threadId]) }
        HomeUiState.Ready(
            followed = rows.filter { it.overview.thread.isFollowed },
            others = rows.filterNot { it.overview.thread.isFollowed },
            thisWeek = AgendaSchedule.thisWeek(ObserveAgendaUseCase.buildAgenda(list), today),
            now = now,
            today = today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )

    /** Hour of the daily auto-summary, or null when it is off. */
    private val _autoSummaryHour = MutableStateFlow<Int?>(null)
    val autoSummaryHour: StateFlow<Int?> = _autoSummaryHour

    /** Re-read on every resume: the schedule may have changed in Settings. */
    fun refreshAutoSummary() {
        viewModelScope.launch {
            _autoSummaryHour.value = if (preferencesRepository.isAutoSummarizationEnabled()) {
                preferencesRepository.getAutoSummarizationHour()
            } else {
                null
            }
        }
    }

    fun setFollowed(threadId: String, followed: Boolean) {
        viewModelScope.launch {
            threadRepository.updateFollowStatus(threadId, followed)
        }
    }

    fun summarize(threadId: String) {
        coordinator.generate(threadId)
    }
}
