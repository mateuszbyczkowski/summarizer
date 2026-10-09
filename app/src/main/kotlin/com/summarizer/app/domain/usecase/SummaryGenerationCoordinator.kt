package com.summarizer.app.domain.usecase

import com.summarizer.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs on-demand summary generation outside any screen, so it survives navigation and at
 * most one generation runs per thread. Screens observe [stateFor]; results land in the database.
 */
@Singleton
class SummaryGenerationCoordinator @Inject constructor(
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {

    sealed interface State {
        data object Running : State
        data class Failed(val error: Throwable) : State
    }

    private val states = MutableStateFlow<Map<String, State>>(emptyMap())

    fun stateFor(threadId: String): Flow<State?> =
        states.map { it[threadId] }.distinctUntilChanged()

    fun generate(threadId: String) {
        var alreadyRunning = false
        states.update { current ->
            alreadyRunning = current[threadId] == State.Running
            if (alreadyRunning) current else current + (threadId to State.Running)
        }
        if (alreadyRunning) return

        scope.launch {
            try {
                generateSummaryUseCase.execute(threadId)
                    .onSuccess { states.update { it - threadId } }
                    .onFailure { error ->
                        Timber.e(error, "Failed to generate summary")
                        states.update { it + (threadId to State.Failed(error)) }
                    }
            } finally {
                // Never leave a thread stuck in Running, even if the job is cancelled
                states.update { current -> if (current[threadId] == State.Running) current - threadId else current }
            }
        }
    }

    fun dismissError(threadId: String) {
        states.update { current ->
            if (current[threadId] is State.Failed) current - threadId else current
        }
    }
}
