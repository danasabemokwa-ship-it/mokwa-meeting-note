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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
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
import com.meetnotes.app.ui.components.EmptyState

private enum class DetailTab(val label: String) { SUMMARY("Minutes"), TRANSCRIPT("Transcript"), ACTIONS("Actions"), NOTES("Notes") }

@Composable
fun MeetingDetailScreen(onBack: () -> Unit, vm: MeetingDetailViewModel = hiltViewModel()) {
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
                        StatusBanner(meeting, onRetry = { vm.retry() }, onDismissNote = { vm.dismissNote() })
                        AudioPlayerCard(meeting.audioPath, meeting.durationMs)
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
                                TabbedPanes(panes, listOf(DetailTab.SUMMARY, DetailTab.ACTIONS, DetailTab.NOTES))
                            }
                        }
                    } else {
                        TabbedPanes(panes, DetailTab.entries)
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
            DetailTab.SUMMARY -> SummaryPane(meeting, defaultTone, actions.size, onGenerate, onEditSummary, onDownload)
            DetailTab.TRANSCRIPT -> TranscriptPane(meeting, onTranscribe, onSaveTranscript)
            DetailTab.ACTIONS -> ActionsPane(actions, onToggle, onEditAction, onDeleteAction, onCalendar, onCopy, onShareActions)
            DetailTab.NOTES -> NotesPane(meeting, onSaveNotes)
        }
    }
}

@Composable
private fun TabbedPanes(panes: DetailPaneCallbacks, tabs: List<DetailTab>) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val index = selected.coerceIn(0, tabs.lastIndex)
    TabRow(selectedTabIndex = index) {
        tabs.forEachIndexed { i, tab ->
            val badge = if (tab == DetailTab.ACTIONS && panes.actions.isNotEmpty()) " (${panes.actions.count { !it.done }})" else ""
            Tab(selected = i == index, onClick = { selected = i }, text = { Text(tab.label + badge, maxLines = 1) })
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
