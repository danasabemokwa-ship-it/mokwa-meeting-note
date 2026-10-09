@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.data.repository.DocumentRepository
import com.meetnotes.app.domain.model.DocKind
import com.meetnotes.app.domain.model.DocStatus
import com.meetnotes.app.domain.model.DocumentItem
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.FileKindBadge
import com.meetnotes.app.ui.components.GradientHeader
import com.meetnotes.app.ui.components.Pill
import com.meetnotes.app.ui.theme.Brand
import com.meetnotes.app.util.Formatters

@Composable
fun DocumentsScreen(
    onOpenDocument: (Long) -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    openPickerOnStart: Boolean = false,
    vm: DocumentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pickerShown by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }
    val pick = { picker.launch(DocumentRepository.PICKER_MIME_TYPES) }

    LaunchedEffect(openPickerOnStart) {
        if (openPickerOnStart && !pickerShown) { pickerShown = true; pick() }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DocEvent.Opened -> onOpenDocument(e.id)
                is DocEvent.Message -> snackbar.showSnackbar(e.text)
                else -> Unit
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                GradientHeader(title = "Documents", subtitle = "Key points from Word, PDF & Excel") {
                    Column {
                        Text(
                            "Add a report, memo, proposal or spreadsheet and get the overview, key points, figures, " +
                                "recommendations and action points.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.size(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = pick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Green),
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add file")
                            }
                            OutlinedButton(
                                onClick = { pasting = true },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Paste text")
                            }
                        }
                    }
                }
            }

            if (!state.usingAi) {
                item {
                    Card(
                        onClick = onSettings,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Summaries are made offline. For sharper, AI-written briefs (and scanned PDFs), set \"Minutes writer\" to Gemini in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Search documents") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FilterChip(selected = state.kind == null, onClick = { vm.setKind(null) }, label = { Text("All") }) }
                        items(DocKind.entries) { k ->
                            FilterChip(selected = state.kind == k, onClick = { vm.setKind(k) }, label = { Text(k.label) })
                        }
                    }
                }
            }

            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.documents.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Default.FolderOpen,
                        title = if (state.query.isBlank() && state.kind == null) "No documents yet" else "No matches",
                        message = if (state.query.isBlank() && state.kind == null)
                            "Tap \"Add file\" to pick a Word (.docx), PDF, Excel (.xlsx) or CSV file. You can also open an attachment in Gmail and choose Share → Mokwa Meeting Note."
                        else "Try another search or filter.",
                    )
                }
                else -> items(state.documents, key = { it.id }) { d ->
                    DocumentCard(d, onClick = { onOpenDocument(d.id) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (pasting) {
        var title by rememberSaveable { mutableStateOf("") }
        var text by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text("Summarise pasted text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, label = { Text("Text (e.g. an email or report)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { pasting = false; vm.importText(title, text) }) { Text("Summarise") }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DocumentCard(d: DocumentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            FileKindBadge(d.kind)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.summary?.title?.ifBlank { null } ?: d.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(Formatters.date(d.createdAt), d.meta, d.summary?.documentType.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    d.status.isBusy -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(d.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    d.status == DocStatus.FAILED -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(d.errorMessage ?: "Couldn't read this file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 2)
                    }
                    else -> {
                        val s = d.summary
                        val preview = s?.keyPoints?.firstOrNull() ?: s?.overview
                        if (!preview.isNullOrBlank()) {
                            Text(
                                preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (s != null) {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (s.keyPoints.isNotEmpty()) Pill("${s.keyPoints.size} key points", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                if (s.actionItems.isNotEmpty()) Pill("${s.actionItems.size} actions", Brand.Gold.copy(alpha = 0.18f), Color(0xFF7A5200))
                                if (s.issues.isNotEmpty()) Pill("${s.issues.size} risks", Brand.Red.copy(alpha = 0.12f), Brand.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
