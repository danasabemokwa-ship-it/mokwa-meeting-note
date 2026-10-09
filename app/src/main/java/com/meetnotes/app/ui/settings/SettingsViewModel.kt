package com.meetnotes.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetnotes.app.ai.transcription.LocalWhisperEngine
import com.meetnotes.app.ai.transcription.WhisperLib
import com.meetnotes.app.data.prefs.ApiProvider
import com.meetnotes.app.data.prefs.SecureKeyStore
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.domain.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyStatus(val saved: Map<ApiProvider, String?> = emptyMap())

data class WhisperStatus(
    val nativeLoaded: Boolean = false,
    val text: String = "",
    val importing: Boolean = false,
    val hasModel: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val keys: SecureKeyStore,
    private val whisper: LocalWhisperEngine,
    private val gmail: com.meetnotes.app.export.GmailComposer,
) : ViewModel() {

    val gmailInstalled: Boolean get() = gmail.isGmailInstalled()

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _keyStatus = MutableStateFlow(readKeys())
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

    private val _whisper = MutableStateFlow(readWhisper())
    val whisperStatus: StateFlow<WhisperStatus> = _whisper.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun saveKey(provider: ApiProvider, value: String) {
        keys.set(provider, value)
        _keyStatus.value = readKeys()
        _message.value = if (value.isBlank()) "${provider.label} key removed" else "${provider.label} key saved securely"
    }

    fun importWhisperModel(uri: Uri) {
        viewModelScope.launch {
            _whisper.update { it.copy(importing = true) }
            runCatching { whisper.importModel(uri) }
                .onSuccess { _message.value = "Model imported: ${it.name}" }
                .onFailure { _message.value = "Import failed: ${it.message}" }
            _whisper.value = readWhisper()
        }
    }

    fun deleteWhisperModel() {
        whisper.deleteModel()
        _whisper.value = readWhisper()
    }

    fun consumeMessage() { _message.value = null }

    private fun readKeys() = KeyStatus(ApiProvider.entries.associateWith { keys.masked(it) })

    private fun readWhisper() = WhisperStatus(
        nativeLoaded = WhisperLib.isLoaded,
        text = whisper.statusText(),
        hasModel = whisper.installedModel() != null,
    )
}
