package com.summarizer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.summarizer.app.ui.screens.threads.ThreadDetailScreen
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

        // TODO: Add onboarding screen
        // composable(Screen.Onboarding.route) { ... }
    }
}
