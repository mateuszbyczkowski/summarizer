package com.summarizer.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.ui.screens.auth.PinSetupScreen
import com.summarizer.app.ui.screens.models.ModelDownloadScreen
import com.summarizer.app.ui.screens.models.StorageLocationScreen
import com.summarizer.app.ui.screens.onboarding.AIProviderChoiceScreen
import com.summarizer.app.ui.screens.onboarding.OpenAISetupScreen
import com.summarizer.app.ui.screens.onboarding.PermissionExplanationScreen
import com.summarizer.app.ui.screens.onboarding.WelcomeScreen
import com.summarizer.app.ui.screens.search.SearchScreen
import com.summarizer.app.ui.screens.settings.SettingsScreen
import com.summarizer.app.ui.screens.summary.SummaryDisplayScreen
import com.summarizer.app.ui.screens.threads.ThreadDetailScreen
import com.summarizer.app.ui.screens.threads.ThreadListScreen
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
    object ThreadList : Screen("thread_list")
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
            Screen.ThreadList.route
        } else {
            ONBOARDING_GRAPH
        }
    }

    // Handle navigation requests from notifications once the graph is ready
    LaunchedEffect(pendingNavigation, startDestination) {
        if (startDestination != Screen.ThreadList.route || pendingNavigation == null) return@LaunchedEffect
        when (pendingNavigation) {
            is PendingNavigation.Thread -> navController.navigate(Screen.ThreadDetail.createRoute(pendingNavigation.threadId)) {
                popUpTo(Screen.ThreadList.route)
                launchSingleTop = true
            }
            is PendingNavigation.Summary -> {
                navController.navigate(Screen.ThreadDetail.createRoute(pendingNavigation.threadId)) {
                    popUpTo(Screen.ThreadList.route)
                }
                navController.navigate(Screen.SummaryDisplay.createRoute(pendingNavigation.threadId)) {
                    launchSingleTop = true
                }
            }
            PendingNavigation.Threads -> navController.popBackStack(Screen.ThreadList.route, inclusive = false)
        }
        onPendingNavigationHandled()
    }

    fun finishOnboarding() {
        scope.launch {
            preferencesRepository.setOnboardingComplete()
            navController.navigate(Screen.ThreadList.route) {
                popUpTo(ONBOARDING_GRAPH) { inclusive = true }
            }
        }
    }

    val slideSpec = tween<IntOffset>(300, easing = EaseInOutCubic)
    val fadeSpec = tween<Float>(300)

    val start = startDestination ?: return
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) + fadeIn(fadeSpec)
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) + fadeOut(fadeSpec)
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) + fadeIn(fadeSpec)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) + fadeOut(fadeSpec)
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
            route = Screen.ThreadList.route,
            enterTransition = { fadeIn(fadeSpec) },
            popEnterTransition = { fadeIn(fadeSpec) }
        ) {
            ThreadListScreen(
                onThreadClick = { threadId ->
                    navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackPressed = { navController.popBackStack() },
                onModelConfigClick = {
                    navController.navigate(Screen.ModelDownload.createRoute())
                },
                onResetComplete = { navController.restartAtOnboarding() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBackPressed = { navController.popBackStack() },
                onThreadClick = { threadId ->
                    navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                }
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
