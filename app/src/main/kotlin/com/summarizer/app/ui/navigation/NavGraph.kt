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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.ui.screens.auth.PinLockScreen
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
import dagger.hilt.android.EntryPointAccessors

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object PermissionExplanation : Screen("permission_explanation")
    object PinSetup : Screen("pin_setup")
    object AIProviderChoice : Screen("ai_provider_choice")
    object OpenAISetup : Screen("openai_setup")
    object StorageLocation : Screen("storage_location")
    object ModelDownload : Screen("model_download")
    object PinLock : Screen("pin_lock")
    object ThreadList : Screen("thread_list")
    object Settings : Screen("settings")
    object Search : Screen("search")
    object ThreadDetail : Screen("thread_detail/{threadId}") {
        fun createRoute(threadId: String) = "thread_detail/$threadId"
    }
    object SummaryDisplay : Screen("summary/{threadId}/{threadName}") {
        fun createRoute(threadId: String, threadName: String) = "summary/$threadId/${java.net.URLEncoder.encode(threadName, "UTF-8")}"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Get repositories from Hilt
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoriesEntryPoint::class.java
        )
    }
    val authRepository = entryPoint.authRepository()
    val preferencesRepository = entryPoint.preferencesRepository()

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val isFirstLaunch = preferencesRepository.isFirstLaunch()
        startDestination = when {
            isFirstLaunch -> Screen.Welcome.route
            !authRepository.hasPin() -> Screen.PinSetup.route
            else -> Screen.PinLock.route
        }
    }

    // Define common animation specs following Material Design 3 guidelines
    val slideAnimationSpec = tween<androidx.compose.ui.unit.IntOffset>(300, easing = EaseInOutCubic)
    val fadeAnimationSpec = tween<Float>(300)

    if (startDestination != null) {
        NavHost(
            navController = navController,
            startDestination = startDestination!!
        ) {
            composable(
                route = Screen.Welcome.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = slideAnimationSpec
                    ) + fadeIn(animationSpec = fadeAnimationSpec)
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = slideAnimationSpec
                    ) + fadeOut(animationSpec = fadeAnimationSpec)
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = slideAnimationSpec
                    ) + fadeIn(animationSpec = fadeAnimationSpec)
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = slideAnimationSpec
                    ) + fadeOut(animationSpec = fadeAnimationSpec)
                }
            ) {
                WelcomeScreen(
                    onContinueClick = {
                        navController.navigate(Screen.PermissionExplanation.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.PermissionExplanation.route,
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideAnimationSpec) +
                    fadeIn(fadeAnimationSpec)
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideAnimationSpec) +
                    fadeOut(fadeAnimationSpec)
                },
                popEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, slideAnimationSpec) +
                    fadeIn(fadeAnimationSpec)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slideAnimationSpec) +
                    fadeOut(fadeAnimationSpec)
                }
            ) {
                PermissionExplanationScreen(
                    onContinueClick = {
                        navController.navigate(Screen.PinSetup.route) {
                            popUpTo(Screen.PermissionExplanation.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.PinSetup.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                PinSetupScreen(
                    onPinSet = {
                        navController.navigate(Screen.AIProviderChoice.route)
                    }
                )
            }

            composable(
                route = Screen.AIProviderChoice.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                AIProviderChoiceScreen(
                    onProviderSelected = { provider ->
                        when (provider) {
                            AIProvider.LOCAL -> {
                                navController.navigate(Screen.StorageLocation.route) {
                                    popUpTo(Screen.AIProviderChoice.route) { inclusive = true }
                                }
                            }
                            AIProvider.OPENAI -> {
                                navController.navigate(Screen.OpenAISetup.route) {
                                    popUpTo(Screen.AIProviderChoice.route) { inclusive = true }
                                }
                            }
                        }
                    }
                )
            }

            composable(
                route = Screen.OpenAISetup.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                OpenAISetupScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.ThreadList.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onBackClick = {
                        navController.navigate(Screen.AIProviderChoice.route) {
                            popUpTo(Screen.OpenAISetup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.StorageLocation.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                val previousRoute = navController.previousBackStackEntry?.destination?.route
                val isFromOnboarding = previousRoute == Screen.AIProviderChoice.route ||
                                       previousRoute == Screen.PinSetup.route ||
                                       previousRoute == Screen.PermissionExplanation.route ||
                                       previousRoute == Screen.Welcome.route
                StorageLocationScreen(
                    onLocationSelected = { location ->
                        if (isFromOnboarding) {
                            navController.navigate(Screen.ModelDownload.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onBackClick = if (!isFromOnboarding) {
                        { navController.popBackStack() }
                    } else {
                        {
                            navController.navigate(Screen.AIProviderChoice.route) {
                                popUpTo(Screen.StorageLocation.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = Screen.ModelDownload.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                val isFromOnboarding = navController.previousBackStackEntry?.destination?.route == Screen.StorageLocation.route
                ModelDownloadScreen(
                    onModelSelected = { model ->
                        if (isFromOnboarding) {
                            navController.navigate(Screen.ThreadList.route) {
                                popUpTo(Screen.ModelDownload.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onSkip = {
                        if (isFromOnboarding) {
                            navController.navigate(Screen.ThreadList.route) {
                                popUpTo(Screen.ModelDownload.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onBackClick = if (!isFromOnboarding) {
                        { navController.popBackStack() }
                    } else null,
                    onStorageClick = {
                        navController.navigate(Screen.StorageLocation.route)
                    }
                )
            }

            composable(
                route = Screen.PinLock.route,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                PinLockScreen(
                    onUnlocked = {
                        navController.navigate(Screen.ThreadList.route) {
                            popUpTo(Screen.PinLock.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.ThreadList.route,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
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

            composable(
                route = Screen.Settings.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                SettingsScreen(
                    onBackPressed = { navController.popBackStack() },
                    onModelConfigClick = {
                        navController.navigate(Screen.ModelDownload.route)
                    },
                    onResetComplete = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.Search.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) {
                SearchScreen(
                    onBackPressed = { navController.popBackStack() },
                    onThreadClick = { threadId ->
                        navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                    }
                )
            }

            composable(
                route = Screen.ThreadDetail.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                ),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                ThreadDetailScreen(
                    threadId = threadId,
                    onBackClick = { navController.popBackStack() },
                    onSummarizeClick = { threadId, threadName ->
                        navController.navigate(Screen.SummaryDisplay.createRoute(threadId, threadName))
                    }
                )
            }

            composable(
                route = Screen.SummaryDisplay.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType },
                    navArgument("threadName") { type = NavType.StringType }
                ),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseInOutCubic)) + fadeOut(tween(300)) }
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                val threadName = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("threadName") ?: "",
                    "UTF-8"
                )
                SummaryDisplayScreen(
                    threadId = threadId,
                    threadName = threadName,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface RepositoriesEntryPoint {
    fun authRepository(): AuthRepository
    fun preferencesRepository(): PreferencesRepository
}
