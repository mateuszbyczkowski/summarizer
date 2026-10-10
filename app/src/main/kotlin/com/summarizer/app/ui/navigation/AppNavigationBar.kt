package com.summarizer.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.R
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
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** The destinations of the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHATS(Screen.Home.route, R.string.nav_chats, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    TASKS(Screen.Tasks.route, R.string.nav_tasks, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    SETTINGS(Screen.Settings.route, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object {
        fun forRoute(route: String?): TopLevelDestination? = entries.firstOrNull { it.route == route }
    }
}

/**
 * Bottom navigation between chats, tasks and settings. The tasks item carries a badge with the
 * open tasks due this week.
 */
@Composable
fun AppNavigationBar(
    current: TopLevelDestination,
    dueSoonTasks: Int,
    onSelect: (TopLevelDestination) -> Unit
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
                    if (destination == TopLevelDestination.TASKS && dueSoonTasks > 0) {
                        val badgeDescription = pluralStringResource(R.plurals.nav_tasks_badge_cd, dueSoonTasks, dueSoonTasks)
                        BadgedBox(
                            badge = {
                                Badge(modifier = Modifier.semantics { contentDescription = badgeDescription }) {
                                    Text(if (dueSoonTasks > 99) "99+" else dueSoonTasks.toString())
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = null)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(destination.label)) }
            )
        }
    }
}

/** State of the app shell: the badge count on the tasks tab. */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    observeAgenda: ObserveAgendaUseCase
) : ViewModel() {

    private val today = minuteTicks()
        .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinctUntilChanged()

    val dueSoonTasks: StateFlow<Int> = combine(observeAgenda(), today) { agenda, day ->
        AgendaSchedule.dueSoonTaskCount(agenda, day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
