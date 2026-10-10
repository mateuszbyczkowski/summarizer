package com.summarizer.app.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingWorkPolicy
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.usecase.ObserveAgendaUseCase
import com.summarizer.app.worker.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads what the "Upcoming" widget shows and keeps placed widgets current.
 *
 * Refreshes when:
 * - the agenda changes (new summary, task ticked off): observed while a widget is placed, debounced;
 * - the app goes to the background and the app lock setting differs from what the widget shows
 *   (the setting can only change inside the app, and the widget is hidden while the app is open);
 * - the day changes (a midnight WorkManager job) or the clock, time zone or language changes.
 *
 * Every refresh bumps [refreshes] as well as calling `updateAll`: a Glance session that is still
 * running only reloads its Glance state on update, so the widget recomposes from this signal.
 */
@Singleton
class UpcomingWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeAgenda: ObserveAgendaUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val workScheduler: WorkScheduler,
    @ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    private val _refreshes = MutableStateFlow(0L)

    /** Incremented on every refresh; running widget sessions reload when it changes. */
    val refreshes: StateFlow<Long> = _refreshes.asStateFlow()

    private val widgetsPlaced = MutableStateFlow(false)

    /** The app lock setting the widget was last rendered with, or null before the first render. */
    @Volatile
    private var shownAppLockEnabled: Boolean? = null

    /** Call once from the application's onCreate (main thread). */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            // Observe the database only while a widget is placed
            widgetsPlaced
                .flatMapLatest { placed -> if (placed) observeAgenda() else emptyFlow() }
                .debounce(AGENDA_DEBOUNCE_MS)
                .collect { refresh() }
        }
        scope.launch { onWidgetsPlaced(hasWidgets()) }
    }

    /** A first widget was placed ([placed] true) or the last one removed. */
    fun onWidgetsPlaced(placed: Boolean) {
        widgetsPlaced.value = placed
        if (placed) {
            workScheduler.scheduleWidgetMidnightRefresh(ExistingWorkPolicy.KEEP)
        } else {
            workScheduler.cancelWidgetMidnightRefresh()
        }
    }

    /** The clock, time zone or language changed: "today" and the formatting may differ. */
    fun onSystemChanged(): Job = scope.launch {
        if (!hasWidgets()) return@launch
        workScheduler.scheduleWidgetMidnightRefresh(ExistingWorkPolicy.REPLACE)
        refresh()
    }

    /** Re-renders every placed widget. */
    suspend fun refresh() {
        _refreshes.update { it + 1 }
        UpcomingWidget().updateAll(context)
    }

    suspend fun hasWidgets(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(UpcomingWidget::class.java).isNotEmpty()

    /** The widget's content now. Reads the database off the main thread. */
    suspend fun loadState(): UpcomingWidgetState = withContext(Dispatchers.IO) {
        try {
            val appLockEnabled = preferencesRepository.isAppLockEnabled()
            val agenda = observeAgenda().first()
            shownAppLockEnabled = appLockEnabled
            UpcomingWidgetModel.build(agenda, LocalDate.now(), appLockEnabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Widget data unavailable")
            UpcomingWidgetState.Unavailable
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch {
            if (widgetsPlaced.value && preferencesRepository.isAppLockEnabled() != shownAppLockEnabled) {
                refresh()
            }
        }
    }

    private companion object {
        const val AGENDA_DEBOUNCE_MS = 1_000L
    }
}
