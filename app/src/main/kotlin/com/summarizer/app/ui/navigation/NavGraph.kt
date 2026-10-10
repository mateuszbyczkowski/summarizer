package com.summarizer.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.ui.screens.auth.PinSetupScreen
import com.summarizer.app.ui.screens.home.HomeScreen
import com.summarizer.app.ui.screens.models.ModelDownloadScreen
import com.summarizer.app.ui.screens.models.StorageLocationScreen
import com.summarizer.app.ui.screens.onboarding.EngineChoiceScreen
import com.summarizer.app.ui.screens.onboarding.NotificationAccessScreen
import com.summarizer.app.ui.screens.onboarding.OnboardingNext
import com.summarizer.app.ui.screens.onboarding.OnboardingNextEffect
import com.summarizer.app.ui.screens.onboarding.OnboardingViewModel
import com.summarizer.app.ui.screens.onboarding.OpenAISetupScreen
import com.summarizer.app.ui.screens.onboarding.WelcomeScreen
import com.summarizer.app.ui.screens.search.SearchScreen
import com.summarizer.app.ui.screens.settings.SettingsScreen
import com.summarizer.app.ui.screens.tasks.TasksScreen
import com.summarizer.app.ui.screens.thread.ThreadScreen
import com.summarizer.app.ui.screens.thread.ThreadTab
import com.summarizer.app.ui.screens.thread.ThreadViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

private const val ONBOARDING_GRAPH = "onboarding"
private const val NO_SUMMARY_ID = -1L

sealed class Screen(val route: String) {
    // Onboarding: three numbered steps, then the OpenAI key and the app PIN when needed
    object Welcome : Screen("welcome")
    object NotificationAccess : Screen("notification_access")
    object EngineChoice : Screen("engine_choice")
    object OpenAISetup : Screen("openai_setup")
    object PinSetup : Screen("pin_setup")
    object StorageLocation : Screen("storage_location")
    object ModelDownload : Screen("model_download")
    object Home : Screen("home")
    object Tasks : Screen("tasks")
    object Settings : Screen("settings")
    object Search : Screen("search")
    object Thread : Screen(
        "thread/{${ThreadViewModel.ARG_THREAD_ID}}" +
            "?${ThreadViewModel.ARG_TAB}={${ThreadViewModel.ARG_TAB}}" +
            "&${ThreadViewModel.ARG_SUMMARY_ID}={${ThreadViewModel.ARG_SUMMARY_ID}}"
    ) {
        /**
         * @param tab Tab to open; null picks the summary when there is one, else the messages
         * @param summaryId Summary to show instead of the latest
         */
        fun createRoute(threadId: String, tab: ThreadTab? = null, summaryId: Long? = null): String =
            buildString {
                append("thread/").append(Uri.encode(threadId))
                append("?${ThreadViewModel.ARG_TAB}=").append(tab?.route.orEmpty())
                append("&${ThreadViewModel.ARG_SUMMARY_ID}=").append(summaryId ?: NO_SUMMARY_ID)
            }
    }
}

/** Navigation requested from outside the app, e.g. by tapping a notification. */
sealed interface PendingNavigation {
    data class Thread(val threadId: String) : PendingNavigation
    data class Summary(val threadId: String) : PendingNavigation
    data object Threads : PendingNavigation
}

@Composable
fun NavGraph(
    pendingNavigation: PendingNavigation?,
    onPendingNavigationHandled: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoriesEntryPoint::class.java
        )
    }
    val preferencesRepository = entryPoint.preferencesRepository()

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (preferencesRepository.hasCompletedOnboarding()) {
            Screen.Home.route
        } else {
            ONBOARDING_GRAPH
        }
    }

    // Handle navigation requests from notifications once the graph is ready
    LaunchedEffect(pendingNavigation, startDestination) {
        if (startDestination != Screen.Home.route || pendingNavigation == null) return@LaunchedEffect
        when (pendingNavigation) {
            is PendingNavigation.Thread -> navController.openThread(pendingNavigation.threadId) {
                popUpTo(Screen.Home.route)
            }
            is PendingNavigation.Summary -> navController.openSummary(pendingNavigation.threadId) {
                popUpTo(Screen.Home.route)
            }
            PendingNavigation.Threads -> navController.popBackStack(Screen.Home.route, inclusive = false)
        }
        onPendingNavigationHandled()
    }

    fun finishOnboarding() {
        scope.launch {
            preferencesRepository.setOnboardingComplete()
            // A second "finish" (a double tap) must not push a second Home
            val inOnboarding = navController.currentBackStackEntry?.destination?.hierarchy
                ?.any { it.route == ONBOARDING_GRAPH } == true
            if (!inOnboarding) return@launch
            navController.navigate(Screen.Home.route) {
                popUpTo(ONBOARDING_GRAPH) { inclusive = true }
            }
        }
    }

    fun onOnboardingNext(next: OnboardingNext) {
        when (next) {
            OnboardingNext.OPENAI_SETUP -> navController.navigate(Screen.OpenAISetup.route) { launchSingleTop = true }
            OnboardingNext.PIN_SETUP -> navController.navigate(Screen.PinSetup.route) { launchSingleTop = true }
            OnboardingNext.FINISH -> finishOnboarding()
        }
    }

    val slideSpec = tween<IntOffset>(300, easing = EaseInOutCubic)
    val fadeSpec = tween<Float>(300)

    // The bottom bar shows on the three top-level destinations only; while it hides, it keeps
    // showing the tab it was on
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTopLevel = TopLevelDestination.forRoute(backStackEntry?.destination?.route)
    var lastTopLevel by remember { mutableStateOf(TopLevelDestination.CHATS) }
    LaunchedEffect(currentTopLevel) { currentTopLevel?.let { lastTopLevel = it } }
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val dueSoonTasks by shellViewModel.dueSoonTasks.collectAsStateWithLifecycle()

    val start = startDestination ?: return
    Scaffold(
        // Screens handle the system bars themselves; this only makes room for the bottom bar
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentTopLevel != null,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                AppNavigationBar(
                    current = currentTopLevel ?: lastTopLevel,
                    dueSoonTasks = dueSoonTasks,
                    onSelect = { navController.navigateToTopLevel(it) }
                )
            }
        }
    ) { shellPadding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .padding(shellPadding)
                .consumeWindowInsets(shellPadding),
            enterTransition = {
                if (isBetweenTabs()) {
                    fadeIn(fadeSpec)
                } else {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) + fadeIn(fadeSpec)
                }
            },
            exitTransition = {
                if (isBetweenTabs()) {
                    fadeOut(fadeSpec)
                } else {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) + fadeOut(fadeSpec)
                }
            },
            popEnterTransition = {
                if (isBetweenTabs()) {
                    fadeIn(fadeSpec)
                } else {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) + fadeIn(fadeSpec)
                }
            },
            popExitTransition = {
                if (isBetweenTabs()) {
                    fadeOut(fadeSpec)
                } else {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) + fadeOut(fadeSpec)
                }
            }
        ) {
            navigation(startDestination = Screen.Welcome.route, route = ONBOARDING_GRAPH) {
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        onContinueClick = { navController.navigate(Screen.NotificationAccess.route) }
                    )
                }

                composable(Screen.NotificationAccess.route) {
                    NotificationAccessScreen(
                        onContinue = {
                            navController.navigate(Screen.EngineChoice.route) { launchSingleTop = true }
                        }
                    )
                }

                composable(Screen.EngineChoice.route) {
                    EngineChoiceScreen(onNext = ::onOnboardingNext)
                }

                composable(Screen.OpenAISetup.route) {
                    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                    OnboardingNextEffect(onboardingViewModel, ::onOnboardingNext)
                    OpenAISetupScreen(
                        onSetupComplete = onboardingViewModel::openAiSetupDone,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Only on phones without a screen lock; not one of the numbered steps
                composable(Screen.PinSetup.route) {
                    PinSetupScreen(onPinSet = ::finishOnboarding)
                }
            }

            composable(Screen.StorageLocation.route) {
                StorageLocationScreen(
                    onLocationSelected = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.ModelDownload.route) {
                ModelDownloadScreen(
                    onBackClick = { navController.popBackStack() },
                    onStorageClick = { navController.navigate(Screen.StorageLocation.route) },
                    // "Try on example": show the summary being written
                    onOpenThread = { threadId -> navController.openThread(threadId, ThreadTab.SUMMARY) }
                )
            }

            composable(
                route = Screen.Home.route,
                enterTransition = { fadeIn(fadeSpec) },
                popEnterTransition = { fadeIn(fadeSpec) }
            ) {
                HomeScreen(
                    onThreadClick = { threadId -> navController.openThread(threadId) },
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onAllTasksClick = { navController.navigateToTopLevel(TopLevelDestination.TASKS) },
                    onChooseEngine = { navController.navigate(Screen.ModelDownload.route) }
                )
            }

            composable(Screen.Tasks.route) {
                TasksScreen(
                    onThreadClick = { threadId -> navController.openThread(threadId) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    // A top-level tab: no back arrow
                    onBackPressed = null,
                    onModelConfigClick = { navController.navigate(Screen.ModelDownload.route) },
                    onResetComplete = { navController.restartAtOnboarding() }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBackPressed = { navController.popBackStack() },
                    onThreadClick = { threadId -> navController.openThread(threadId, ThreadTab.MESSAGES) },
                    onSummaryClick = { threadId, summaryId -> navController.openSummary(threadId, summaryId) }
                )
            }

            composable(
                route = Screen.Thread.route,
                arguments = listOf(
                    navArgument(ThreadViewModel.ARG_THREAD_ID) { type = NavType.StringType },
                    navArgument(ThreadViewModel.ARG_TAB) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(ThreadViewModel.ARG_SUMMARY_ID) {
                        type = NavType.LongType
                        defaultValue = NO_SUMMARY_ID
                    }
                )
            ) {
                ThreadScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Opens a thread on its summary when it has one, else on its messages. The only place, with
 * [openSummary], that builds the thread route.
 */
private fun NavHostController.openThread(
    threadId: String,
    tab: ThreadTab? = null,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(Screen.Thread.createRoute(threadId, tab), builder)
}

/** Opens a thread on its summary tab: the latest summary, or [summaryId] (e.g. a search match). */
private fun NavHostController.openSummary(
    threadId: String,
    summaryId: Long? = null,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(Screen.Thread.createRoute(threadId, ThreadTab.SUMMARY, summaryId), builder)
}

/** Switches tabs: one copy of each tab on the stack, each keeping its state, home at the bottom. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isBetweenTabs(): Boolean =
    TopLevelDestination.forRoute(initialState.destination.route) != null &&
        TopLevelDestination.forRoute(targetState.destination.route) != null

private fun NavHostController.restartAtOnboarding() {
    navigate(ONBOARDING_GRAPH) {
        popUpTo(graph.id) { inclusive = true }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface RepositoriesEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}
