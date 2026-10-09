package com.meetnotes.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import com.meetnotes.app.data.repository.DocumentRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val documents: DocumentRepository,
) : ViewModel() {
    /** null until DataStore has been read, so we don't flash the wrong start screen. */
    val onboardingDone: StateFlow<Boolean?> = settings.settings
        .map<AppSettings, Boolean?> { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Set when the recording notification is tapped. */
    val openRecorder = MutableStateFlow(false)

    /** Id of a document that was shared into the app and should be opened. */
    val openDocument = MutableStateFlow<Long?>(null)

    /** Message shown when a shared file can't be imported. */
    val importError = MutableStateFlow<String?>(null)

    fun importShared(uri: Uri) = viewModelScope.launch {
        runCatching { documents.importFromUri(uri) }
            .onSuccess { openDocument.value = it }
            .onFailure { importError.value = it.message ?: "Couldn't open this file" }
    }

    fun importSharedText(subject: String?, text: String) = viewModelScope.launch {
        runCatching { documents.importText(subject.orEmpty().ifBlank { "Shared text" }, text) }
            .onSuccess { openDocument.value = it }
            .onFailure { importError.value = it.message }
    }
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
                    start?.let { MeetNotesNavHost(startDestination = it, openRecorder = vm.openRecorder, openDocument = vm.openDocument) }
                    val error by vm.importError.collectAsStateWithLifecycle()
                    LaunchedEffect(error) {
                        error?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                            vm.importError.value = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_RECORDER, false)) {
            vm.openRecorder.value = true
            intent.removeExtra(EXTRA_OPEN_RECORDER)
            return
        }
        // A Word / PDF / Excel file shared or opened from another app (e.g. a Gmail attachment).
        if (intent.getBooleanExtra(EXTRA_HANDLED, false)) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> vm.importShared(stream)
                    !text.isNullOrBlank() -> vm.importSharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT), text)
                    else -> return
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { vm.importShared(it) } ?: return
            else -> return
        }
        // Don't import the same file again after rotation / process restore.
        intent.putExtra(EXTRA_HANDLED, true)
    }

    companion object {
        const val EXTRA_OPEN_RECORDER = "open_recorder"
        private const val EXTRA_HANDLED = "mokwa_handled"
    }
}
