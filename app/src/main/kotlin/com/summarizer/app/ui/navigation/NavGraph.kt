package com.summarizer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.summarizer.app.ui.screens.threads.ThreadListScreen

sealed class Screen(val route: String) {
    object ThreadList : Screen("thread_list")
    object ThreadDetail : Screen("thread_detail/{threadId}") {
        fun createRoute(threadId: String) = "thread_detail/$threadId"
    }
    object Onboarding : Screen("onboarding")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.ThreadList.route
    ) {
        composable(Screen.ThreadList.route) {
            ThreadListScreen(
                onThreadClick = { threadId ->
                    navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                }
            )
        }

        // TODO: Add other screens
        // composable(Screen.ThreadDetail.route) { ... }
        // composable(Screen.Onboarding.route) { ... }
    }
}
