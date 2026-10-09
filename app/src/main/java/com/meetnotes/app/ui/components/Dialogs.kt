@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Priority
import com.meetnotes.app.export.ExportFormat
import com.meetnotes.app.export.GmailComposer
import com.meetnotes.app.util.DueDates

/**
 * Full action-point editor: task, owner (+ email for Gmail reminders), due date with a calendar
 * picker, priority and notes.
 */
@Composable
fun ActionEditorDialog(
    initial: ActionItem?,
    meetingId: Long,
    onDismiss: () -> Unit,
    onSave: (ActionItem) -> Unit,
) {
    var task by rememberSaveable { mutableStateOf(initial?.task.orEmpty()) }
    var owner by rememberSaveable { mutableStateOf(initial?.owner?.takeIf { !it.equals("TBD", true) }.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initial?.ownerEmail.orEmpty()) }
    var due by rememberSaveable { mutableStateOf(initial?.dueDate?.takeIf { !it.equals("TBD", true) }.orEmpty()) }
    var dueAt by rememberSaveable { mutableStateOf(initial?.dueAt) }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: Priority.MEDIUM) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf(false) }

    val emailValid = email.isBlank() || GmailComposer.parseEmails(email).isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New action point" else "Edit action point") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = task, onValueChange = { task = it }, label = { Text("Task") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = owner, onValueChange = { owner = it }, label = { Text("Responsible person") },
                    placeholder = { Text("TBD") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() }, label = { Text("Their email (for Gmail reminders)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = !emailValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it; dueAt = DueDates.parse(it) },
                    label = { Text("Due date") },
                    placeholder = { Text("e.g. Friday, 15 October") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { dueAt?.let { Text("→ ${DueDates.label(it)}") } },
                    trailingIcon = {
                        Row {
                            if (due.isNotEmpty()) {
                                IconButton(onClick = { due = ""; dueAt = null }) { Icon(Icons.Default.Clear, contentDescription = "Clear date") }
                            }
                            IconButton(onClick = { picking = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Pick a date") }
                        }
                    },
                )
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = priority == p,
                            onClick = { priority = p },
                            shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                        ) { Text(p.label) }
                    }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Notes / progress update") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = task.isNotBlank() && emailValid,
                onClick = {
                    val base = initial ?: ActionItem(meetingId = meetingId, task = "")
                    onSave(
                        base.copy(
                            task = task.trim(),
                            owner = owner.trim().ifBlank { "TBD" },
                            ownerEmail = GmailComposer.parseEmails(email).firstOrNull().orEmpty(),
                            dueDate = due.trim().ifBlank { "TBD" },
                            dueAt = dueAt,
                            priority = priority,
                            notes = notes.trim(),
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dueAt?.let(DueDates::toPickerUtc))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val local = DueDates.fromPickerUtc(utc)
                        dueAt = local
                        due = DueDates.label(local)
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

/** Options for sending minutes or a brief through Gmail. */
data class EmailRequest(
    val to: List<String>,
    val cc: List<String>,
    val attachment: ExportFormat?,
    val includeTranscript: Boolean,
)

@Composable
fun EmailDialog(
    title: String,
    defaultTo: String,
    onDismiss: () -> Unit,
    onSend: (EmailRequest) -> Unit,
    showTranscriptOption: Boolean = true,
    gmailInstalled: Boolean = true,
) {
    var to by rememberSaveable { mutableStateOf(defaultTo) }
    var cc by rememberSaveable { mutableStateOf("") }
    var attachment by rememberSaveable { mutableStateOf<ExportFormat?>(ExportFormat.PDF) }
    var transcript by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = to, onValueChange = { to = it }, label = { Text("To") },
                    placeholder = { Text("name@example.com, …") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supportingText = { Text("Separate addresses with commas. You can also add them in Gmail.") },
                )
                OutlinedTextField(
                    value = cc, onValueChange = { cc = it }, label = { Text("Cc (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach", style = MaterialTheme.typography.labelLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = attachment == ExportFormat.PDF, onClick = { attachment = ExportFormat.PDF }, label = { Text("PDF") })
                    FilterChip(selected = attachment == ExportFormat.WORD, onClick = { attachment = ExportFormat.WORD }, label = { Text("Word") })
                    FilterChip(selected = attachment == null, onClick = { attachment = null }, label = { Text("None") })
                }
                if (showTranscriptOption) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = transcript, onCheckedChange = { transcript = it })
                        Text("Include full transcript in the attachment", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    if (gmailInstalled) "Gmail opens with everything filled in — review and tap Send."
                    else "Gmail isn't installed, so you'll choose an email app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSend(EmailRequest(GmailComposer.parseEmails(to), GmailComposer.parseEmails(cc), attachment, transcript))
            }) { Text(if (gmailInstalled) "Open in Gmail" else "Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
