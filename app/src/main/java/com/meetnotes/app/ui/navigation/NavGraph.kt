package com.meetnotes.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meetnotes.app.ui.detail.MeetingDetailScreen
import com.meetnotes.app.ui.home.HomeScreen
import com.meetnotes.app.ui.onboarding.OnboardingScreen
import com.meetnotes.app.ui.record.RecordScreen
import com.meetnotes.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val RECORD = "record"
    const val SETTINGS = "settings"
    const val DETAIL = "meeting/{id}"
    fun detail(id: Long) = "meeting/$id"
}

@Composable
fun MeetNotesNavHost(startDestination: String, openRecorder: MutableStateFlow<Boolean>) {
    val nav = rememberNavController()
    val shouldOpenRecorder by openRecorder.collectAsStateWithLifecycle()

    LaunchedEffect(shouldOpenRecorder) {
        if (shouldOpenRecorder && startDestination == Routes.HOME) {
            nav.navigate(Routes.RECORD) { launchSingleTop = true }
        }
        openRecorder.value = false
    }

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { it / 10 } },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onRecord = { nav.navigate(Routes.RECORD) { launchSingleTop = true } },
                onOpenMeeting = { nav.navigate(Routes.detail(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.navigate(Routes.detail(id)) { popUpTo(Routes.RECORD) { inclusive = true } }
                },
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            MeetingDetailScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
