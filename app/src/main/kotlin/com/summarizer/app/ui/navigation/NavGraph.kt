package com.summarizer.app.ui.navigation

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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.ui.screens.auth.PinSetupScreen
import com.summarizer.app.ui.screens.home.HomeScreen
import com.summarizer.app.ui.screens.models.ModelDownloadScreen
import com.summarizer.app.ui.screens.models.StorageLocationScreen
import com.summarizer.app.ui.screens.onboarding.AIProviderChoiceScreen
import com.summarizer.app.ui.screens.onboarding.OpenAISetupScreen
import com.summarizer.app.ui.screens.onboarding.PermissionExplanationScreen
import com.summarizer.app.ui.screens.onboarding.WelcomeScreen
import com.summarizer.app.ui.screens.search.SearchScreen
import com.summarizer.app.ui.screens.settings.SettingsScreen
import com.summarizer.app.ui.screens.summary.SummaryDisplayScreen
import com.summarizer.app.ui.screens.tasks.TasksScreen
import com.summarizer.app.ui.screens.threads.ThreadDetailScreen
import com.summarizer.app.util.BiometricHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

private const val ONBOARDING_GRAPH = "onboarding"
private const val ARG_ONBOARDING = "onboarding"

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object PermissionExplanation : Screen("permission_explanation")
    object PinSetup : Screen("pin_setup")
    object AIProviderChoice : Screen("ai_provider_choice")
    object OpenAISetup : Screen("openai_setup")
    object StorageLocation : Screen("storage_location?$ARG_ONBOARDING={$ARG_ONBOARDING}") {
        fun createRoute(onboarding: Boolean = false) = "storage_location?$ARG_ONBOARDING=$onboarding"
    }
    object ModelDownload : Screen("model_download?$ARG_ONBOARDING={$ARG_ONBOARDING}") {
        fun createRoute(onboarding: Boolean = false) = "model_download?$ARG_ONBOARDING=$onboarding"
    }
    object Home : Screen("home")
    object Tasks : Screen("tasks")
    object Settings : Screen("settings")
    object Search : Screen("search")
    object ThreadDetail : Screen("thread_detail/{threadId}") {
        fun createRoute(threadId: String) = "thread_detail/$threadId"
    }
    object SummaryDisplay : Screen("summary/{threadId}") {
        fun createRoute(threadId: String) = "summary/$threadId"
    }
}

/** Navigation requested from outside the app, e.g. by tapping a notification. */
sealed interface PendingNavigation {
    data class Thread(val threadId: String) : PendingNavigation
    data class Summary(val threadId: String) : PendingNavigation
    data object Threads : PendingNavigation
}

private val onboardingArgument = navArgument(ARG_ONBOARDING) {
    type = NavType.BoolType
    defaultValue = false
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
                launchSingleTop = true
            }
            is PendingNavigation.Summary -> {
                navController.openThread(pendingNavigation.threadId) {
                    popUpTo(Screen.Home.route)
                }
                navController.navigate(Screen.SummaryDisplay.createRoute(pendingNavigation.threadId)) {
                    launchSingleTop = true
                }
            }
            PendingNavigation.Threads -> navController.popBackStack(Screen.Home.route, inclusive = false)
        }
        onPendingNavigationHandled()
    }

    fun finishOnboarding() {
        scope.launch {
            preferencesRepository.setOnboardingComplete()
            navController.navigate(Screen.Home.route) {
                popUpTo(ONBOARDING_GRAPH) { inclusive = true }
            }
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
                        onContinueClick = { navController.navigate(Screen.PermissionExplanation.route) }
                    )
                }

                composable(Screen.PermissionExplanation.route) {
                    PermissionExplanationScreen(
                        onContinueClick = {
                            // The phone's own screen lock protects the app; an app PIN only without one
                            val next = if (BiometricHelper.isDeviceSecure(context)) Screen.AIProviderChoice else Screen.PinSetup
                            navController.navigate(next.route)
                        }
                    )
                }

                composable(Screen.PinSetup.route) {
                    PinSetupScreen(
                        onPinSet = {
                            // The PIN cannot be changed by navigating back to this screen
                            navController.navigate(Screen.AIProviderChoice.route) {
                                popUpTo(ONBOARDING_GRAPH) { inclusive = false }
                            }
                        }
                    )
                }

                composable(Screen.AIProviderChoice.route) {
                    AIProviderChoiceScreen(
                        onProviderSelected = { provider ->
                            when (provider) {
                                AIProvider.LOCAL -> navController.navigate(Screen.StorageLocation.createRoute(onboarding = true))
                                AIProvider.OPENAI -> navController.navigate(Screen.OpenAISetup.route)
                            }
                        }
                    )
                }

                composable(Screen.OpenAISetup.route) {
                    OpenAISetupScreen(
                        onSetupComplete = ::finishOnboarding,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.StorageLocation.route, arguments = listOf(onboardingArgument)) { entry ->
                val isOnboarding = entry.arguments?.getBoolean(ARG_ONBOARDING) == true
                StorageLocationScreen(
                    onLocationSelected = {
                        if (isOnboarding) {
                            navController.navigate(Screen.ModelDownload.createRoute(onboarding = true))
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.ModelDownload.route, arguments = listOf(onboardingArgument)) { entry ->
                val isOnboarding = entry.arguments?.getBoolean(ARG_ONBOARDING) == true
                ModelDownloadScreen(
                    onModelSelected = {
                        if (isOnboarding) finishOnboarding() else navController.popBackStack()
                    },
                    onSkip = {
                        if (isOnboarding) finishOnboarding() else navController.popBackStack()
                    },
                    onBackClick = if (isOnboarding) null else {
                        { navController.popBackStack() }
                    },
                    onStorageClick = {
                        navController.navigate(Screen.StorageLocation.createRoute())
                    }
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
                    onAllTasksClick = { navController.navigateToTopLevel(TopLevelDestination.TASKS) }
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
                    onModelConfigClick = {
                        navController.navigate(Screen.ModelDownload.createRoute())
                    },
                    onResetComplete = { navController.restartAtOnboarding() }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBackPressed = { navController.popBackStack() },
                    onThreadClick = { threadId -> navController.openThread(threadId) }
                )
            }

            composable(
                route = Screen.ThreadDetail.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                ThreadDetailScreen(
                    threadId = threadId,
                    onBackClick = { navController.popBackStack() },
                    onSummarizeClick = { id, _ ->
                        navController.navigate(Screen.SummaryDisplay.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.SummaryDisplay.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) {
                SummaryDisplayScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Opens a thread. The only place that builds the thread route, so the thread screen can be
 * swapped here.
 */
private fun NavHostController.openThread(threadId: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(Screen.ThreadDetail.createRoute(threadId), builder)
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
