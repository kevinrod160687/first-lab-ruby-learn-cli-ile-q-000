package dev.kevin.trail.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.kevin.trail.ui.permission.BackgroundLocationScreen
import dev.kevin.trail.ui.settings.SettingsScreen
import dev.kevin.trail.ui.today.TodayScreen

@Composable
fun TrailApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "today") {
        composable("today") {
            TodayScreen(
                onSettings = { nav.navigate("settings") },
                onBackgroundRationale = { nav.navigate("background") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("background") {
            BackgroundLocationScreen(onDone = { nav.popBackStack() })
        }
    }
}
