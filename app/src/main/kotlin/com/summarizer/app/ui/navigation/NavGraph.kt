package com.summarizer.app.ui.navigation

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
import com.summarizer.app.ui.screens.auth.PinLockScreen
import com.summarizer.app.ui.screens.auth.PinSetupScreen
import com.summarizer.app.ui.screens.models.ModelDownloadScreen
import com.summarizer.app.ui.screens.models.StorageLocationScreen
import com.summarizer.app.ui.screens.onboarding.PermissionExplanationScreen
import com.summarizer.app.ui.screens.onboarding.WelcomeScreen
import com.summarizer.app.ui.screens.threads.ThreadDetailScreen
import com.summarizer.app.ui.screens.threads.ThreadListScreen
import dagger.hilt.android.EntryPointAccessors

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object PermissionExplanation : Screen("permission_explanation")
    object PinSetup : Screen("pin_setup")
    object StorageLocation : Screen("storage_location")
    object ModelDownload : Screen("model_download")
    object PinLock : Screen("pin_lock")
    object ThreadList : Screen("thread_list")
    object ThreadDetail : Screen("thread_detail/{threadId}") {
        fun createRoute(threadId: String) = "thread_detail/$threadId"
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

    if (startDestination != null) {
        NavHost(
            navController = navController,
            startDestination = startDestination!!
        ) {
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    onContinueClick = {
                        navController.navigate(Screen.PermissionExplanation.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PermissionExplanation.route) {
                PermissionExplanationScreen(
                    onContinueClick = {
                        navController.navigate(Screen.PinSetup.route) {
                            popUpTo(Screen.PermissionExplanation.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PinSetup.route) {
                PinSetupScreen(
                    onPinSet = {
                        navController.navigate(Screen.StorageLocation.route) {
                            popUpTo(Screen.PinSetup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.StorageLocation.route) {
                StorageLocationScreen(
                    onLocationSelected = { location ->
                        navController.navigate(Screen.ModelDownload.route) {
                            popUpTo(Screen.StorageLocation.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.ModelDownload.route) {
                ModelDownloadScreen(
                    onModelSelected = { model ->
                        navController.navigate(Screen.ThreadList.route) {
                            popUpTo(Screen.ModelDownload.route) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Screen.ThreadList.route) {
                            popUpTo(Screen.ModelDownload.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PinLock.route) {
                PinLockScreen(
                    onUnlocked = {
                        navController.navigate(Screen.ThreadList.route) {
                            popUpTo(Screen.PinLock.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.ThreadList.route) {
                ThreadListScreen(
                    onThreadClick = { threadId ->
                        navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                    }
                )
            }

            composable(
                route = Screen.ThreadDetail.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                ThreadDetailScreen(
                    threadId = threadId,
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
