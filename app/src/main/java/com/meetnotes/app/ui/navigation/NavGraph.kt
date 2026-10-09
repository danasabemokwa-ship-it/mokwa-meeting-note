package com.meetnotes.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meetnotes.app.domain.repository.MeetingRepository
import com.meetnotes.app.ui.actions.ActionsScreen
import com.meetnotes.app.ui.components.AppBottomBar
import com.meetnotes.app.ui.components.TopDestination
import com.meetnotes.app.ui.detail.MeetingDetailScreen
import com.meetnotes.app.ui.documents.DocumentDetailScreen
import com.meetnotes.app.ui.documents.DocumentsScreen
import com.meetnotes.app.ui.home.HomeScreen
import com.meetnotes.app.ui.onboarding.OnboardingScreen
import com.meetnotes.app.ui.record.RecordScreen
import com.meetnotes.app.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ACTIONS = "actions"
    const val DOCUMENTS = "documents?pick={pick}"
    const val SETTINGS_TAB = "settings_tab"
    const val RECORD = "record"
    const val SETTINGS = "settings"
    const val DETAIL = "meeting/{id}"
    const val DOCUMENT = "document/{id}"
    fun detail(id: Long) = "meeting/$id"
    fun document(id: Long) = "document/$id"
    fun documents(pick: Boolean = false) = "documents?pick=$pick"

    fun of(dest: TopDestination) = when (dest) {
        TopDestination.HOME -> HOME
        TopDestination.ACTIONS -> ACTIONS
        TopDestination.DOCUMENTS -> documents()
        TopDestination.SETTINGS -> SETTINGS_TAB
    }
}

/** Badge count for the bottom bar. */
@HiltViewModel
class ShellViewModel @Inject constructor(repo: MeetingRepository) : ViewModel() {
    val openActions: StateFlow<Int> = repo.observeOpenActionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

private fun NavHostController.switchTab(dest: TopDestination) {
    navigate(Routes.of(dest)) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * @param openRecorder set when the recording notification is tapped.
 * @param openDocument id of a document shared into the app (e.g. a Gmail attachment).
 */
@Composable
fun MeetNotesNavHost(
    startDestination: String,
    openRecorder: MutableStateFlow<Boolean>,
    openDocument: MutableStateFlow<Long?> = MutableStateFlow(null),
) {
    val nav = rememberNavController()
    val shell: ShellViewModel = hiltViewModel()
    val openActions by shell.openActions.collectAsStateWithLifecycle()
    val shouldOpenRecorder by openRecorder.collectAsStateWithLifecycle()
    val sharedDocument by openDocument.collectAsStateWithLifecycle()

    LaunchedEffect(shouldOpenRecorder) {
        if (shouldOpenRecorder && startDestination == Routes.HOME) {
            nav.navigate(Routes.RECORD) { launchSingleTop = true }
        }
        openRecorder.value = false
    }
    LaunchedEffect(sharedDocument) {
        val id = sharedDocument ?: return@LaunchedEffect
        if (startDestination == Routes.HOME) nav.navigate(Routes.document(id)) { launchSingleTop = true }
        openDocument.value = null
    }

    val bar: @Composable (TopDestination) -> Unit = { current ->
        AppBottomBar(current = current, openActions = openActions, onNavigate = { nav.switchTab(it) })
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
        composable(Routes.HOME, enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) }) {
            HomeScreen(
                onRecord = { nav.navigate(Routes.RECORD) { launchSingleTop = true } },
                onOpenMeeting = { nav.navigate(Routes.detail(it)) },
                onSettings = { nav.switchTab(TopDestination.SETTINGS) },
                onActions = { nav.switchTab(TopDestination.ACTIONS) },
                onDocuments = { nav.switchTab(TopDestination.DOCUMENTS) },
                onAddDocument = {
                    nav.navigate(Routes.documents(pick = true)) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                    }
                },
                bottomBar = { bar(TopDestination.HOME) },
            )
        }
        composable(Routes.ACTIONS, enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) }) {
            ActionsScreen(
                onOpenMeeting = { nav.navigate(Routes.detail(it)) },
                bottomBar = { bar(TopDestination.ACTIONS) },
            )
        }
        composable(
            Routes.DOCUMENTS,
            arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false }),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
        ) { entry ->
            DocumentsScreen(
                onOpenDocument = { nav.navigate(Routes.document(it)) },
                onSettings = { nav.switchTab(TopDestination.SETTINGS) },
                bottomBar = { bar(TopDestination.DOCUMENTS) },
                openPickerOnStart = entry.arguments?.getBoolean("pick") == true,
            )
        }
        composable(Routes.SETTINGS_TAB, enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) }) {
            SettingsScreen(bottomBar = { bar(TopDestination.SETTINGS) })
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
            MeetingDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.DOCUMENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            DocumentDetailScreen(onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
