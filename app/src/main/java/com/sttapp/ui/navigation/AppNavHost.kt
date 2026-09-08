package com.sttapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sttapp.ui.home.HomeScreen
import com.sttapp.ui.session.SessionScreen
import com.sttapp.ui.sessions.SessionsScreen
import com.sttapp.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SESSIONS = "sessions"
    const val SESSION = "session/{sessionId}"
    const val SETTINGS = "settings"

    fun session(id: Long): String = "session/$id"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SESSIONS) {
            SessionsScreen(
                onOpenSession = { id -> navController.navigate(Routes.session(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
            SessionScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
