package com.summarizer.app.ui.screens.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.SummarizationMode
import com.summarizer.app.domain.model.Summary
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import com.summarizer.app.service.WhatsAppNotificationParser
import com.summarizer.app.util.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Tabs of the thread screen; [route] is the value of the route's `tab` argument. */
enum class ThreadTab(val route: String) {
    SUMMARY("summary"),
    MESSAGES("messages");

    companion object {
        fun fromRoute(value: String?): ThreadTab? = entries.firstOrNull { it.route == value }
    }
}

sealed interface ThreadUiState {
    data object Loading : ThreadUiState

    /**
     * @property summaries The thread's summaries, newest first
     * @property shown The summary on screen: the latest, or an earlier one picked from the history
     * @property newMessages Messages newer than the latest summary (0 without a summary)
     * @property generation The summary being generated or the failed attempt, if any
     */
    data class Ready(
        val thread: Thread?,
        val threadName: String,
        val messages: List<Message>,
        val timeline: List<TimelineItem>,
        val summaries: List<Summary>,
        val shown: Summary?,
        val newMessages: Int,
        val mode: SummarizationMode,
        val generation: SummaryGenerationCoordinator.State?,
        val today: LocalDate
    ) : ThreadUiState {
        val latest: Summary? get() = summaries.firstOrNull()
        val isGenerating: Boolean get() = generation is SummaryGenerationCoordinator.State.Running
    }
}

/**
 * The thread screen: the thread's summaries (with history), its messages, and summary generation
 * through [SummaryGenerationCoordinator].
 */
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageRepository: MessageRepository,
    private val summaryRepository: SummaryRepository,
    private val threadRepository: ThreadRepository,
    private val threadSettingsRepository: ThreadSettingsRepository,
    private val coordinator: SummaryGenerationCoordinator
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>(ARG_THREAD_ID).orEmpty()

    /** Tab named in the route, if any; otherwise the screen picks one once data has loaded. */
    val requestedTab: ThreadTab? = ThreadTab.fromRoute(savedStateHandle.get<String>(ARG_TAB))

    /** An earlier summary picked from the history; null shows the latest. */
    private val selectedSummaryId = MutableStateFlow(
        savedStateHandle.get<Long>(ARG_SUMMARY_ID)?.takeIf { it >= 0 }
    )

    private val today = minuteTicks()
        .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinctUntilChanged()

    /** Everything that changes only with the data, kept apart from progress ticks and the clock. */
    private data class Content(
        val thread: Thread?,
        val messages: List<Message>,
        val timeline: List<TimelineItem>,
        val summaries: List<Summary>,
        val newMessages: Int,
        val mode: SummarizationMode
    )

    private val content = combine(
        threadRepository.observeThread(threadId),
        messageRepository.getMessagesForThread(threadId),
        summaryRepository.getSummariesForThread(threadId),
        threadSettingsRepository.getSettingsFlow(threadId).map { it?.summarizationMode ?: SummarizationMode.INCREMENTAL }
    ) { thread, messages, summaries, mode ->
        val latest = summaries.firstOrNull()
        Content(
            thread = thread,
            messages = messages,
            timeline = MessageTimeline.build(messages, latest?.endTimestamp) {
                it.sender == WhatsAppNotificationParser.SELF_SENDER
            },
            summaries = summaries,
            newMessages = ThreadSummaryRules.newMessageCount(messages, latest),
            mode = mode
        )
    }

    val uiState: StateFlow<ThreadUiState> = combine(
        content,
        coordinator.stateFor(threadId),
        selectedSummaryId,
        today
    ) { content, generation, selectedId, day ->
        ThreadUiState.Ready(
            thread = content.thread,
            threadName = content.thread?.threadName ?: content.messages.firstOrNull()?.threadName.orEmpty(),
            messages = content.messages,
            timeline = content.timeline,
            summaries = content.summaries,
            shown = content.summaries.firstOrNull { it.id == selectedId } ?: content.summaries.firstOrNull(),
            newMessages = content.newMessages,
            mode = content.mode,
            generation = generation,
            today = day
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState.Loading)

    fun selectSummary(summaryId: Long) {
        selectedSummaryId.value = summaryId
    }

    /** Summarizes in the thread's mode (by default only the new messages). */
    fun update() {
        selectedSummaryId.value = null
        coordinator.generate(threadId)
    }

    /** Summarizes the whole conversation once, without changing the thread's mode. */
    fun fullResummary() {
        selectedSummaryId.value = null
        coordinator.generate(threadId, full = true)
    }

    fun cancelGeneration() {
        coordinator.cancel(threadId)
    }

    fun dismissError() {
        viewModelScope.launch(Dispatchers.IO) { coordinator.dismissError(threadId) }
    }

    fun setMode(mode: SummarizationMode) {
        viewModelScope.launch {
            threadSettingsRepository.getOrCreateSettings(threadId)
            threadSettingsRepository.updateSummarizationMode(threadId, mode)
        }
    }

    fun setFollowed(followed: Boolean) {
        viewModelScope.launch { threadRepository.updateFollowStatus(threadId, followed) }
    }

    fun setTaskDone(summaryId: Long, index: Int, done: Boolean) {
        viewModelScope.launch { summaryRepository.setActionItemDone(summaryId, index, done) }
    }

    /** +1 or -1; choosing the current rating again clears it. */
    fun rate(summary: Summary, rating: Int) {
        viewModelScope.launch {
            summaryRepository.setRating(summary.id, if (summary.rating == rating) null else rating)
        }
    }

    companion object {
        const val ARG_THREAD_ID = "threadId"
        const val ARG_TAB = "tab"

        /** A summary to show instead of the latest, e.g. the one a search matched; -1 for the latest */
        const val ARG_SUMMARY_ID = "summaryId"
    }
}
