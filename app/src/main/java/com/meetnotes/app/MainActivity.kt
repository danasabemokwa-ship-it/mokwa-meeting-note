package com.meetnotes.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.domain.model.AppSettings
import com.meetnotes.app.ui.navigation.MeetNotesNavHost
import com.meetnotes.app.ui.navigation.Routes
import com.meetnotes.app.ui.theme.MeetNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** null until DataStore has been read, so we don't flash the wrong start screen. */
    val onboardingDone: StateFlow<Boolean?> = settings.settings
        .map<AppSettings, Boolean?> { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Set when the recording notification is tapped. */
    val openRecorder = MutableStateFlow(false)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            MeetNotesTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val done by vm.onboardingDone.collectAsStateWithLifecycle()
                    var start by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(done) {
                        if (start == null && done != null) start = if (done == true) Routes.HOME else Routes.ONBOARDING
                    }
                    start?.let { MeetNotesNavHost(startDestination = it, openRecorder = vm.openRecorder) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RECORDER, false) == true) {
            vm.openRecorder.value = true
            intent.removeExtra(EXTRA_OPEN_RECORDER)
        }
    }

    companion object {
        const val EXTRA_OPEN_RECORDER = "open_recorder"
    }
}
