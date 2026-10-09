@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.documents

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.domain.model.DocStatus
import com.meetnotes.app.domain.model.DocumentItem
import com.meetnotes.app.domain.model.Priority
import com.meetnotes.app.export.ExportFormat
import com.meetnotes.app.ui.components.Avatar
import com.meetnotes.app.ui.components.BulletList
import com.meetnotes.app.ui.components.EmailDialog
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.FileKindBadge
import com.meetnotes.app.ui.components.NumberedList
import com.meetnotes.app.ui.components.Pill
import com.meetnotes.app.ui.components.PriorityPill
import com.meetnotes.app.ui.components.SectionCard
import com.meetnotes.app.ui.theme.Brand
import com.meetnotes.app.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun DocumentDetailScreen(onBack: () -> Unit, vm: DocumentDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var emailing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullText by remember { mutableStateOf<String?>(null) }

    val saveLaunchers = ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { vm.onSaveLocationChosen(it) }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DocEvent.Launch -> try { context.startActivity(e.intent) } catch (x: ActivityNotFoundException) {
                    snackbar.showSnackbar("No app available to open this")
                }
                is DocEvent.Message -> snackbar.showSnackbar(e.text)
                is DocEvent.Copy -> { clipboard.setText(AnnotatedString(e.text)); snackbar.showSnackbar("Copied") }
                is DocEvent.SaveAs -> try { saveLaunchers.getValue(e.format).launch(e.suggestedName) } catch (x: ActivityNotFoundException) {
                    snackbar.showSnackbar("No file manager available to save the file")
                }
                DocEvent.Deleted -> onBack()
                is DocEvent.Opened -> Unit
            }
        }
    }

    val doc = state.document

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(doc?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (doc != null) {
                        IconButton(onClick = { emailing = true }, enabled = doc.summary != null) {
                            Icon(Icons.Default.Email, contentDescription = "Email key points with Gmail")
                        }
                        Box {
                            IconButton(onClick = { shareMenu = true }, enabled = doc.summary != null) {
                                Icon(Icons.Default.Share, contentDescription = "Download or share")
                            }
                            DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Download brief (PDF)") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = { shareMenu = false; vm.download(ExportFormat.PDF) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Download brief (Word)") },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                    onClick = { shareMenu = false; vm.download(ExportFormat.WORD) },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Share brief as PDF") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = { shareMenu = false; vm.share(ExportFormat.PDF) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as text (WhatsApp)") },
                                    leadingIcon = { Icon(Icons.Default.TextSnippet, contentDescription = null) },
                                    onClick = { shareMenu = false; vm.share(ExportFormat.TEXT) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy key points") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = { shareMenu = false; vm.copySummary() },
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Open original file") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                                    onClick = { menu = false; vm.openOriginal() },
                                )
                                DropdownMenuItem(
                                    text = { Text("View extracted text") },
                                    leadingIcon = { Icon(Icons.Default.Subject, contentDescription = null) },
                                    onClick = { menu = false; scope.launch { fullText = vm.fullText() } },
                                )
                                DropdownMenuItem(
                                    text = { Text("Re-analyse") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    enabled = !doc.status.isBusy,
                                    onClick = { menu = false; vm.reanalyse() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = { menu = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            doc == null -> EmptyState(
                icon = Icons.Default.ErrorOutline, title = "Document not found", message = "It may have been deleted.",
                actionLabel = "Go back", onAction = onBack, modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DocumentHeader(doc)
                when {
                    doc.status.isBusy -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                if (doc.status == DocStatus.EXTRACTING) "Reading the document…" else "Finding the key points…",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                "Large files can take a minute. You can leave this screen.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    doc.status == DocStatus.FAILED -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(doc.errorMessage ?: "Couldn't read this file.", modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::reanalyse) { Text("Retry") }
                        }
                    }
                }
                doc.summary?.let { SummaryContent(doc, onEmail = { emailing = true }, onDownload = vm::download, onAddToTracker = vm::addActionsToTracker) }
            }
        }
    }

    if (doc != null) {
        if (emailing) {
            EmailDialog(
                title = "Email key points",
                defaultTo = state.settings.defaultRecipients,
                showTranscriptOption = false,
                gmailInstalled = vm.gmailInstalled,
                onDismiss = { emailing = false },
                onSend = { emailing = false; vm.email(it) },
            )
        }
        if (renaming) {
            var name by remember { mutableStateOf(doc.name) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text("Rename document") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
                confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { renaming = false; vm.rename(name) }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete document?") },
                text = { Text("The imported file and its summary will be removed from the app. The original on your phone or in Gmail is not affected.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
    fullText?.let { text ->
        Dialog(onDismissRequest = { fullText = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Extracted text") },
                        navigationIcon = { IconButton(onClick = { fullText = null }) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                        actions = { IconButton(onClick = { vm.copyText() }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy text") } },
                    )
                },
            ) { p ->
                SelectionContainer(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(text.ifBlank { "No text could be extracted." }.take(200_000), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DocumentHeader(d: DocumentItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FileKindBadge(d.kind, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val s = d.summary
                if (s != null && s.documentType.isNotBlank()) {
                    Text(s.documentType.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(s?.title?.ifBlank { null } ?: d.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    listOf(d.kind.label, d.meta, Formatters.date(d.createdAt)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SummaryContent(
    d: DocumentItem,
    onEmail: () -> Unit,
    onDownload: (ExportFormat) -> Unit,
    onAddToTracker: () -> Unit,
) {
    val s = d.summary ?: return
    if (s.overview.isNotBlank()) {
        SectionCard("Overview", Icons.Default.Subject) {
            Text(s.overview, style = MaterialTheme.typography.bodyLarge)
        }
    }
    SectionCard("Key Points", Icons.Default.Lightbulb, count = s.keyPoints.size.takeIf { it > 0 }) {
        NumberedList(s.keyPoints, emptyText = "No key points were found.")
    }
    if (s.keyFigures.isNotEmpty()) {
        SectionCard("Key Figures", Icons.Default.Insights, count = s.keyFigures.size) { BulletList(s.keyFigures) }
    }
    if (s.recommendations.isNotEmpty()) {
        SectionCard("Recommendations", Icons.Default.TipsAndUpdates, count = s.recommendations.size) { BulletList(s.recommendations) }
    }
    if (s.issues.isNotEmpty()) {
        SectionCard("Issues & Risks", Icons.Default.ReportProblem, count = s.issues.size) { BulletList(s.issues) }
    }
    if (s.actionItems.isNotEmpty()) {
        SectionCard(
            "Action Points", Icons.Default.Checklist, count = s.actionItems.size,
            action = { TextButton(onClick = onAddToTracker) { Text("Track") } },
        ) {
            Column {
                s.actionItems.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(a.task, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(a.owner, 20.dp)
                        Text(a.owner, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Pill(
                            if (a.dueDate.equals("TBD", true)) "No date" else a.dueDate,
                            MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PriorityPill(Priority.parse(a.priority))
                    }
                }
            }
        }
    }
    if (s.generatedBy.isNotBlank()) {
        Text("Summarised by ${s.generatedBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
    Button(onClick = onEmail, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Email key points with Gmail")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onDownload(ExportFormat.PDF) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp), tint = Brand.PdfRed)
            Spacer(Modifier.width(6.dp))
            Text("PDF brief")
        }
        OutlinedButton(onClick = { onDownload(ExportFormat.WORD) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = Brand.Blue)
            Spacer(Modifier.width(6.dp))
            Text("Word brief")
        }
    }
    if (s.actionItems.isNotEmpty()) {
        OutlinedButton(onClick = onAddToTracker, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add action points to tracker")
        }
    }
}
