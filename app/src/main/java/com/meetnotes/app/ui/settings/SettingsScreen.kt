@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.meetnotes.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.BuildConfig
import com.meetnotes.app.data.prefs.ApiProvider
import com.meetnotes.app.data.prefs.LANGUAGES
import com.meetnotes.app.domain.model.AudioQuality
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.model.TranscriptionEngineType

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val keys by vm.keyStatus.collectAsStateWithLifecycle()
    val whisper by vm.whisperStatus.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::importWhisperModel)
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---------------- Transcription
                SettingsCard("Transcription", Icons.Default.RecordVoiceOver) {
                    TranscriptionEngineType.entries.forEach { type ->
                        val ready = when (type) {
                            TranscriptionEngineType.DEMO -> true
                            TranscriptionEngineType.LOCAL_WHISPER -> whisper.nativeLoaded && whisper.hasModel
                            TranscriptionEngineType.OPENAI_WHISPER -> keys.saved[ApiProvider.OPENAI] != null
                            TranscriptionEngineType.GEMINI -> keys.saved[ApiProvider.GEMINI] != null
                        }
                        RadioRow(
                            title = type.label,
                            subtitle = when (type) {
                                TranscriptionEngineType.DEMO -> "Inserts a sample transcript — for trying the app"
                                TranscriptionEngineType.LOCAL_WHISPER -> whisper.text
                                else -> if (ready) "Ready" else "Needs an API key (below)"
                            },
                            selected = s.transcriptionEngine == type,
                            warning = !ready,
                            onClick = { vm.update { it.copy(transcriptionEngine = type) } },
                        )
                    }
                    Spacer(Modifier.padding(top = 4.dp))
                    Dropdown(
                        label = "Spoken language",
                        value = LANGUAGES.firstOrNull { it.first == s.language }?.second ?: s.language,
                        options = LANGUAGES.map { it.second },
                    ) { index -> vm.update { it.copy(language = LANGUAGES[index].first) } }

                    SwitchRow(
                        title = "Nigerian speech mode",
                        subtitle = "Tunes transcription and minutes for Nigerian accents, Pidgin, mixed Hausa/Yoruba/Igbo and Nigerian English expressions",
                        checked = s.nigerianSpeech,
                    ) { v -> vm.update { it.copy(nigerianSpeech = v) } }
                    GlossaryField(s.glossary) { v -> vm.update { it.copy(glossary = v) } }

                    Spacer(Modifier.padding(top = 8.dp))
                    Text("On-device Whisper model", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (!whisper.nativeLoaded) "This build doesn't include the whisper.cpp native library. See README → On-device Whisper."
                        else "Import a ggml model file (e.g. ggml-base.bin or ggml-small.bin) downloaded to your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                            enabled = !whisper.importing,
                        ) { Text(if (whisper.hasModel) "Replace model" else "Import model") }
                        if (whisper.hasModel) TextButton(onClick = vm::deleteWhisperModel) { Text("Remove") }
                        if (whisper.importing) CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }

                // ---------------- Summaries
                SettingsCard("Minutes & summaries", Icons.Default.AutoAwesome) {
                    SummarizerType.entries.forEach { type ->
                        val ready = when (type) {
                            SummarizerType.OFFLINE_RULES -> true
                            SummarizerType.OPENAI -> keys.saved[ApiProvider.OPENAI] != null
                            SummarizerType.GEMINI -> keys.saved[ApiProvider.GEMINI] != null
                            SummarizerType.ANTHROPIC -> keys.saved[ApiProvider.ANTHROPIC] != null
                        }
                        RadioRow(
                            title = type.label,
                            subtitle = when (type) {
                                SummarizerType.OFFLINE_RULES -> "Works without internet; less polished than an AI model"
                                else -> if (ready) "Ready · falls back to offline if unavailable" else "Needs an API key (below)"
                            },
                            selected = s.summarizer == type,
                            warning = !ready,
                            onClick = { vm.update { it.copy(summarizer = type) } },
                        )
                    }
                    Text("Default tone", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryTone.entries.forEach { tone ->
                            FilterChip(
                                selected = s.defaultTone == tone,
                                onClick = { vm.update { it.copy(defaultTone = tone) } },
                                label = { Text(tone.label) },
                            )
                        }
                    }
                    ModelField("OpenAI model", s.openAiModel) { v -> vm.update { it.copy(openAiModel = v) } }
                    ModelField("Gemini model", s.geminiModel) { v -> vm.update { it.copy(geminiModel = v) } }
                    ModelField("Anthropic model", s.anthropicModel) { v -> vm.update { it.copy(anthropicModel = v) } }
                }

                // ---------------- API keys
                SettingsCard("API keys", Icons.Default.Key) {
                    Text(
                        "Optional. Keys are encrypted with the Android Keystore and only sent to the provider they belong to. " +
                            "Audio and transcripts are sent to that provider when you choose a cloud engine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ApiProvider.entries.forEach { provider ->
                        ApiKeyField(
                            provider = provider,
                            saved = keys.saved[provider],
                            onSave = { vm.saveKey(provider, it) },
                            onGetKey = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.consoleUrl))) }
                            },
                        )
                    }
                }

                // ---------------- Recording
                SettingsCard("Recording", Icons.Default.Mic) {
                    AudioQuality.entries.forEach { q ->
                        RadioRow(
                            title = q.name.lowercase().replaceFirstChar { it.uppercase() },
                            subtitle = q.label.substringAfter("· "),
                            selected = s.audioQuality == q,
                            onClick = { vm.update { it.copy(audioQuality = q) } },
                        )
                    }
                    SwitchRow(
                        title = "Process automatically",
                        subtitle = "Transcribe and write minutes as soon as a recording is saved",
                        checked = s.autoProcess,
                    ) { v -> vm.update { it.copy(autoProcess = v) } }
                }

                // ---------------- Storage
                SettingsCard("Storage", Icons.Default.Storage) {
                    val options = listOf(0 to "Never", 30 to "After 30 days", 60 to "After 60 days", 90 to "After 90 days", 180 to "After 6 months")
                    Dropdown(
                        label = "Auto-delete old meetings",
                        value = options.firstOrNull { it.first == s.autoDeleteDays }?.second ?: "${s.autoDeleteDays} days",
                        options = options.map { it.second },
                    ) { index -> vm.update { it.copy(autoDeleteDays = options[index].first) } }
                    Text(
                        "Deletes the recording, transcript, minutes and action items. Export anything you want to keep first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "Mokwa Meeting Note ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, warning: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onChange, role = Role.Switch).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Dropdown(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole field opens the menu.
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(i) })
            }
        }
    }
}

@Composable
private fun GlossaryField(value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Names & terms to recognise") },
        placeholder = { Text("e.g. Alhaji Sani, Hajiya Rakiya, Mokwa LGA, NPHCDA") },
        supportingText = {
            Text("Comma-separated people, places and acronyms used in your meetings. This greatly improves spelling.")
        },
        minLines = 2,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        trailingIcon = {
            if (text.trim() != value.trim()) TextButton(onClick = { onSave(text.trim()) }) { Text("Save") }
        },
    )
}

@Composable
private fun ModelField(label: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        trailingIcon = {
            if (text.trim() != value && text.isNotBlank()) TextButton(onClick = { onSave(text.trim()) }) { Text("Save") }
        },
    )
}

@Composable
private fun ApiKeyField(provider: ApiProvider, saved: String?, onSave: (String) -> Unit, onGetKey: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("${provider.label} API key") },
            placeholder = { Text(saved ?: "Not set") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Hide key" else "Show key",
                    )
                }
            },
            supportingText = { Text(if (saved != null) "Saved ($saved)" else "Not set") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = { onSave(text); text = "" }, enabled = text.isNotBlank()) { Text("Save key") }
            if (saved != null) TextButton(onClick = { onSave("") }) { Text("Remove") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onGetKey) { Text("Get a key") }
        }
    }
}
