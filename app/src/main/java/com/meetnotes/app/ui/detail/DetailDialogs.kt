@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.MinutesSummary

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename meeting") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Title") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete meeting?") },
        text = { Text("The recording, transcript, minutes and action items will be permanently deleted.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ActionItemDialog(
    initial: ActionItem?,
    meetingId: Long,
    onDismiss: () -> Unit,
    onSave: (ActionItem) -> Unit,
) {
    var task by rememberSaveable { mutableStateOf(initial?.task.orEmpty()) }
    var owner by rememberSaveable { mutableStateOf(initial?.owner?.takeIf { it != "TBD" }.orEmpty()) }
    var due by rememberSaveable { mutableStateOf(initial?.dueDate?.takeIf { it != "TBD" }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New action item" else "Edit action item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = task, onValueChange = { task = it }, label = { Text("Task") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = owner, onValueChange = { owner = it }, label = { Text("Owner") },
                    placeholder = { Text("TBD") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = due, onValueChange = { due = it }, label = { Text("Due date") },
                    placeholder = { Text("e.g. 15 October 2026 or Friday") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = task.isNotBlank(),
                onClick = {
                    val base = initial ?: ActionItem(meetingId = meetingId, task = "")
                    onSave(base.copy(task = task.trim(), owner = owner.trim().ifBlank { "TBD" }, dueDate = due.trim().ifBlank { "TBD" }))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Full-screen editor for every section of the minutes (one list item per line). */
@Composable
fun SummaryEditDialog(summary: MinutesSummary, onDismiss: () -> Unit, onSave: (MinutesSummary) -> Unit) {
    var title by rememberSaveable { mutableStateOf(summary.title) }
    var dateTime by rememberSaveable { mutableStateOf(summary.dateTime) }
    var participants by rememberSaveable { mutableStateOf(summary.participants.joinToString("\n")) }
    var keyPoints by rememberSaveable { mutableStateOf(summary.keyPoints.joinToString("\n")) }
    var decisions by rememberSaveable { mutableStateOf(summary.decisions.joinToString("\n")) }
    var nextSteps by rememberSaveable { mutableStateOf(summary.nextSteps.joinToString("\n")) }
    val scroll = rememberScrollState()

    fun String.toItems() = split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit minutes") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                    actions = {
                        TextButton(onClick = {
                            onSave(
                                summary.copy(
                                    title = title.trim(),
                                    dateTime = dateTime.trim(),
                                    participants = participants.toItems(),
                                    keyPoints = keyPoints.toItems(),
                                    decisions = decisions.toItems(),
                                    nextSteps = nextSteps.toItems(),
                                )
                            )
                        }) { Text("Save") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(scroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Meeting title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dateTime, { dateTime = it }, label = { Text("Date & time") }, modifier = Modifier.fillMaxWidth())
                MultiLineField("Participants (one per line)", participants) { participants = it }
                MultiLineField("Key discussion points (one per line)", keyPoints) { keyPoints = it }
                MultiLineField("Decisions made (one per line)", decisions) { decisions = it }
                MultiLineField("Next steps (one per line)", nextSteps) { nextSteps = it }
                Text("Action items are edited in the Actions tab.")
            }
        }
    }
}

@Composable
private fun MultiLineField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}
