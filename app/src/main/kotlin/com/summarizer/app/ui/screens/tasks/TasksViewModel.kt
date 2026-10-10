package com.summarizer.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.model.AgendaItem
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.domain.usecase.AgendaSchedule
import com.summarizer.app.domain.usecase.ObserveAgendaUseCase
import com.summarizer.app.util.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
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

sealed interface TasksUiState {
    data object Loading : TasksUiState

    data class Ready(
        val groups: List<Pair<AgendaSchedule.Bucket, List<AgendaItem>>>,
        val today: LocalDate
    ) : TasksUiState
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    observeAgenda: ObserveAgendaUseCase,
    private val summaryRepository: SummaryRepository
) : ViewModel() {

    private val today = minuteTicks()
        .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinctUntilChanged()

    val uiState: StateFlow<TasksUiState> = combine(observeAgenda(), today) { agenda, day ->
        TasksUiState.Ready(groups = AgendaSchedule.group(agenda, day), today = day)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasksUiState.Loading
    )

    fun setDone(item: AgendaItem, done: Boolean) {
        if (item.kind != AgendaItem.Kind.TASK) return
        viewModelScope.launch {
            summaryRepository.setActionItemDone(item.summaryId, item.index, done)
        }
    }
}
