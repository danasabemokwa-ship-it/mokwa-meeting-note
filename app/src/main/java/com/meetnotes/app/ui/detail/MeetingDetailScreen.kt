@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.detail

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.export.ExportFormat
import com.meetnotes.app.ui.components.EmailDialog
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.theme.Brand
import com.meetnotes.app.domain.model.TranscriptionEngineType

private enum class DetailTab(val label: String) { SUMMARY("Minutes"), TRANSCRIPT("Transcript"), ACTIONS("Actions"), NOTES("Notes") }

@Composable
fun MeetingDetailScreen(onBack: () -> Unit, onOpenSettings: () -> Unit = {}, vm: MeetingDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    var showExportMenu by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var includeTranscript by rememberSaveable { mutableStateOf(true) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingSummary by remember { mutableStateOf(false) }
    var editingAction by remember { mutableStateOf<ActionItem?>(null) }
    var addingAction by remember { mutableStateOf(false) }
    var emailing by remember { mutableStateOf(false) }
    var confirmRetranscribe by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // One system "Save as" launcher per file type (the MIME type is fixed per launcher).
    val saveLaunchers = ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
            vm.onSaveLocationChosen(uri)
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DetailEvent.Launch -> try {
                    context.startActivity(event.intent)
                } catch (e: ActivityNotFoundException) {
                    snackbar.showSnackbar("No app available to handle this")
                }
                is DetailEvent.Message -> snackbar.showSnackbar(event.text)
                is DetailEvent.Copy -> {
                    clipboard.setText(AnnotatedString(event.text))
                    snackbar.showSnackbar("Action items copied")
                }
                is DetailEvent.SaveAs -> try {
                    saveLaunchers.getValue(event.format).launch(event.suggestedName)
                } catch (e: ActivityNotFoundException) {
                    snackbar.showSnackbar("No file manager available to save the file")
                }
                DetailEvent.Deleted -> onBack()
            }
        }
    }

    val meeting = state.meeting

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(meeting?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (meeting != null) {
                        IconButton(onClick = { emailing = true }, enabled = meeting.summary != null) {
                            Icon(Icons.Default.Email, contentDescription = "Email minutes with Gmail")
                        }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) { Icon(Icons.Default.Share, contentDescription = "Export and share") }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                listOf(ExportFormat.PDF, ExportFormat.WORD).forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text("Download ${if (format == ExportFormat.PDF) "PDF" else "Word (.docx)"}") },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                        onClick = { showExportMenu = false; vm.download(format, includeTranscript) },
                                    )
                                }
                                HorizontalDivider()
                                listOf(
                                    ExportFormat.PDF to Icons.Default.PictureAsPdf,
                                    ExportFormat.WORD to Icons.Default.Description,
                                    ExportFormat.MARKDOWN to Icons.Default.Code,
                                    ExportFormat.TEXT to Icons.Default.TextSnippet,
                                ).forEach { (format, icon) ->
                                    DropdownMenuItem(
                                        text = { Text("Share as ${format.label}") },
                                        leadingIcon = { Icon(icon, contentDescription = null) },
                                        onClick = { showExportMenu = false; vm.export(format, includeTranscript) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Include transcript") },
                                    leadingIcon = { Checkbox(checked = includeTranscript, onCheckedChange = null) },
                                    onClick = { includeTranscript = !includeTranscript },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Copy action items") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = { showExportMenu = false; vm.copyActionItems() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share audio recording") },
                                    leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null) },
                                    onClick = { showExportMenu = false; vm.shareAudio() },
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = { showOverflow = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Re-transcribe recording") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    enabled = !meeting.status.isBusy,
                                    onClick = { showOverflow = false; confirmRetranscribe = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Email action owners") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    onClick = { showOverflow = false; vm.emailOwners() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { showOverflow = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            meeting == null -> EmptyState(
                icon = Icons.Default.ErrorOutline,
                title = "Meeting not found",
                message = "It may have been deleted.",
                actionLabel = "Go back",
                onAction = onBack,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val panes = DetailPaneCallbacks(
                    meeting = meeting,
                    actions = state.actions,
                    onEmail = { emailing = true },
                    onOpenActions = { selectedTab = DetailTab.entries.indexOf(DetailTab.ACTIONS) },
                    onRemind = { vm.remind(it) },
                    onEmailOwners = { vm.emailOwners() },
                    defaultTone = state.settings.defaultTone,
                    onTranscribe = { vm.transcribe() },
                    onSaveTranscript = { vm.saveTranscript(it) },
                    onGenerate = { vm.generateMinutes(it) },
                    onDownload = { vm.download(it, includeTranscript) },
                    onEditSummary = { editingSummary = true },
                    onToggle = { vm.toggleAction(it) },
                    onEditAction = { if (it == null) addingAction = true else editingAction = it },
                    onDeleteAction = { vm.deleteAction(it) },
                    onCalendar = { vm.addToCalendar(it) },
                    onCopy = { vm.copyActionItems() },
                    onShareActions = { vm.shareActionItems() },
                    onSaveNotes = { n, t -> vm.saveNotes(n, t) },
                )
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val demoText = meeting.transcript?.startsWith("[Demo transcript") == true
                        if (demoText) {
                            DemoTranscriptBanner(
                                demoEngine = state.settings.transcriptionEngine == TranscriptionEngineType.DEMO,
                                onSettings = onOpenSettings,
                                onRetranscribe = { vm.retranscribe() },
                            )
                        }
                        StatusBanner(meeting, onRetry = { vm.retry() }, onDismissNote = { vm.dismissNote() })
                        if (meeting.audioPath.isNotBlank()) AudioPlayerCard(meeting.audioPath, meeting.durationMs)
                    }
                    if (wide) {
                        // Tablet / landscape: transcript on the left, minutes & actions on the right.
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                            ) {
                                Text("Transcript", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.padding(top = 8.dp))
                                panes.Pane(DetailTab.TRANSCRIPT)
                            }
                            Column(Modifier.weight(1f).fillMaxSize()) {
                                TabbedPanes(panes, listOf(DetailTab.SUMMARY, DetailTab.ACTIONS, DetailTab.NOTES), selectedTab) { selectedTab = it }
                            }
                        }
                    } else {
                        TabbedPanes(panes, DetailTab.entries, selectedTab) { selectedTab = it }
                    }
                }
            }
        }
    }

    if (meeting != null) {
        if (renaming) RenameDialog(meeting.title, onDismiss = { renaming = false }) { vm.rename(it); renaming = false }
        if (confirmDelete) ConfirmDeleteDialog(onDismiss = { confirmDelete = false }) { confirmDelete = false; vm.delete() }
        if (editingSummary && meeting.summary != null) {
            SummaryEditDialog(meeting.summary, onDismiss = { editingSummary = false }) { vm.saveSummary(it); editingSummary = false }
        }
        if (emailing) {
            EmailDialog(
                title = "Email minutes",
                defaultTo = state.settings.defaultRecipients,
                gmailInstalled = vm.gmailInstalled,
                onDismiss = { emailing = false },
                onSend = { emailing = false; vm.emailMinutes(it) },
            )
        }
        if (confirmRetranscribe) {
            AlertDialog(
                onDismissRequest = { confirmRetranscribe = false },
                title = { Text("Re-transcribe this meeting?") },
                text = {
                    Text(
                        "The recording will be transcribed again with \"${state.settings.transcriptionEngine.label}\" and new minutes " +
                            "and action points will replace the current ones."
                    )
                },
                confirmButton = { TextButton(onClick = { confirmRetranscribe = false; vm.retranscribe() }) { Text("Re-transcribe") } },
                dismissButton = { TextButton(onClick = { confirmRetranscribe = false }) { Text("Cancel") } },
            )
        }
        if (addingAction || editingAction != null) {
            ActionItemDialog(
                initial = editingAction,
                meetingId = meeting.id,
                onDismiss = { addingAction = false; editingAction = null },
                onSave = { vm.saveAction(it); addingAction = false; editingAction = null },
            )
        }
    }
}

private class DetailPaneCallbacks(
    val meeting: Meeting,
    val actions: List<ActionItem>,
    val onEmail: () -> Unit,
    val onOpenActions: () -> Unit,
    val onRemind: (ActionItem) -> Unit,
    val onEmailOwners: () -> Unit,
    val defaultTone: SummaryTone,
    val onTranscribe: () -> Unit,
    val onSaveTranscript: (String) -> Unit,
    val onGenerate: (SummaryTone) -> Unit,
    val onDownload: (ExportFormat) -> Unit,
    val onEditSummary: () -> Unit,
    val onToggle: (ActionItem) -> Unit,
    val onEditAction: (ActionItem?) -> Unit,
    val onDeleteAction: (ActionItem) -> Unit,
    val onCalendar: (ActionItem) -> Unit,
    val onCopy: () -> Unit,
    val onShareActions: () -> Unit,
    val onSaveNotes: (String, String) -> Unit,
) {
    @Composable
    fun Pane(tab: DetailTab) {
        when (tab) {
            DetailTab.SUMMARY -> SummaryPane(meeting, defaultTone, actions, onGenerate, onEditSummary, onDownload, onEmail, onOpenActions)
            DetailTab.TRANSCRIPT -> TranscriptPane(meeting, onTranscribe, onSaveTranscript)
            DetailTab.ACTIONS -> ActionsPane(actions, onToggle, onEditAction, onDeleteAction, onCalendar, onRemind, onEmailOwners, onCopy, onShareActions)
            DetailTab.NOTES -> NotesPane(meeting, onSaveNotes)
        }
    }
}

@Composable
private fun TabbedPanes(panes: DetailPaneCallbacks, tabs: List<DetailTab>, selectedAll: Int, onSelect: (Int) -> Unit) {
    // [selectedAll] indexes DetailTab.entries so both layouts share one selection.
    val index = tabs.indexOf(DetailTab.entries.getOrElse(selectedAll) { DetailTab.SUMMARY }).let { if (it < 0) 0 else it }
    TabRow(selectedTabIndex = index) {
        tabs.forEachIndexed { i, tab ->
            val badge = if (tab == DetailTab.ACTIONS && panes.actions.isNotEmpty()) " (${panes.actions.count { !it.done }})" else ""
            Tab(selected = i == index, onClick = { onSelect(DetailTab.entries.indexOf(tab)) }, text = { Text(tab.label + badge, maxLines = 1) })
        }
    }
    // Each tab keeps its own scroll position.
    val scroll = rememberScrollState()
    LaunchedEffect(index) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        panes.Pane(tabs[index])
        Spacer(Modifier.padding(bottom = 32.dp))
    }
}

@Composable
private fun StatusBanner(meeting: Meeting, onRetry: () -> Unit, onDismissNote: () -> Unit) {
    val status = meeting.status
    AnimatedVisibility(visible = status.isBusy) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (status == MeetingStatus.TRANSCRIBING) "Transcribing the recording…" else "Writing the minutes…",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("Restart") }
                }
                Spacer(Modifier.padding(top = 8.dp))
                val p = meeting.progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "You can leave this screen — processing continues in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (status == MeetingStatus.FAILED) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    meeting.errorMessage ?: "Something went wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    } else if (!status.isBusy && !meeting.errorMessage.isNullOrBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(meeting.errorMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissNote) { Text("OK") }
            }
        }
    }
}

@Composable
private fun DemoTranscriptBanner(demoEngine: Boolean, onSettings: () -> Unit, onRetranscribe: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Brand.Amber.copy(alpha = 0.16f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFF9A5B00))
                Spacer(Modifier.width(10.dp))
                Text("These minutes are sample text", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                if (demoEngine) "The Demo engine doesn't listen to your recording. Choose Gemini or OpenAI in Settings, then tap Re-transcribe."
                else "This meeting was processed in Demo mode. Tap Re-transcribe to process your real recording.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row {
                if (demoEngine) TextButton(onClick = onSettings) { Text("Open Settings") }
                else TextButton(onClick = onRetranscribe) { Text("Re-transcribe now") }
            }
        }
    }
}
