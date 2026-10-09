@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.meetnotes.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.export.ExportFormat
import com.meetnotes.app.ui.components.ActionCard
import com.meetnotes.app.ui.components.Avatar
import com.meetnotes.app.ui.components.BulletList
import com.meetnotes.app.ui.components.DuePill
import com.meetnotes.app.ui.components.NumberedList
import com.meetnotes.app.ui.components.PriorityPill
import com.meetnotes.app.ui.components.ProgressLine
import com.meetnotes.app.ui.components.SectionCard
import com.meetnotes.app.ui.theme.Brand
import com.meetnotes.app.domain.model.Priority
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.SectionTitle
import com.meetnotes.app.util.Formatters

// ------------------------------------------------------------------ Summary / minutes

@Composable
fun SummaryPane(
    meeting: Meeting,
    defaultTone: SummaryTone,
    actions: List<ActionItem>,
    onGenerate: (SummaryTone) -> Unit,
    onEdit: () -> Unit,
    onDownload: (ExportFormat) -> Unit,
    onEmail: () -> Unit,
    onOpenActions: () -> Unit,
) {
    var tone by rememberSaveable { mutableStateOf(defaultTone) }
    val summary = meeting.summary
    val busy = meeting.status.isBusy
    val canGenerate = !meeting.transcript.isNullOrBlank() && !busy

    if (summary == null) {
        EmptyState(
            icon = Icons.Default.Summarize,
            title = if (meeting.status == MeetingStatus.SUMMARIZING) "Writing the minutes…" else "No minutes yet",
            message = if (meeting.transcript.isNullOrBlank()) {
                "Transcribe the recording first, then generate structured minutes with action points."
            } else "Generate structured minutes: discussion points, decisions, action points and next steps.",
        )
        if (canGenerate) ToneAndGenerate(tone, { tone = it }, "Generate minutes") { onGenerate(tone) }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Title card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("MINUTES OF MEETING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(summary.title.ifBlank { meeting.title }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    summary.dateTime.ifBlank { Formatters.dateTime(meeting.createdAt) } + " · " + Formatters.duration(meeting.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(actions.count { it.done }, actions.size)
                }
            }
        }

        SectionCard("Participants", Icons.Default.Groups, count = summary.participants.size.takeIf { it > 0 }) {
            if (summary.participants.isEmpty()) {
                Text("Not detected — tap Edit minutes to add names.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.participants.forEach { name ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(name, 24.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Key Discussion Points", Icons.Default.RecordVoiceOver, count = summary.keyPoints.size.takeIf { it > 0 }) {
            BulletList(summary.keyPoints)
        }

        SectionCard("Decisions / Resolutions", Icons.Default.Gavel, count = summary.decisions.size.takeIf { it > 0 }) {
            NumberedList(summary.decisions)
        }

        SectionCard(
            "Action Points", Icons.Default.Checklist,
            count = actions.size.takeIf { it > 0 },
            action = { if (actions.isNotEmpty()) TextButton(onClick = onOpenActions) { Text("Manage") } },
        ) {
            if (actions.isEmpty()) {
                Text("None recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Table header
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("Task", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Owner · Due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    actions.forEachIndexed { i, a ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenActions).padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(24.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.task,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (a.done) TextDecoration.LineThrough else null,
                                )
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(a.owner, 20.dp)
                                    Text(a.owner, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    DuePill(a)
                                    if (a.priority != Priority.MEDIUM) PriorityPill(a.priority)
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Next Steps / Follow-up", Icons.Default.Flag, count = summary.nextSteps.size.takeIf { it > 0 }) {
            BulletList(summary.nextSteps)
        }

        if (summary.generatedBy.isNotBlank()) {
            Text(
                "Generated by ${summary.generatedBy}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // Primary actions
        Button(onClick = onEmail, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Email minutes with Gmail")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onDownload(ExportFormat.PDF) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("PDF")
            }
            OutlinedButton(onClick = { onDownload(ExportFormat.WORD) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Word")
            }
            OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
        }
        if (canGenerate) {
            ToneAndGenerate(tone, { tone = it }, "Regenerate minutes") { onGenerate(tone) }
            Text(
                "Regenerating replaces the minutes and the action-point checklist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToneAndGenerate(tone: SummaryTone, onTone: (SummaryTone) -> Unit, label: String, onGenerate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Tone", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTone.entries.forEach { t ->
                FilterChip(selected = t == tone, onClick = { onTone(t) }, label = { Text(t.label) })
            }
        }
        Button(onClick = onGenerate, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

// ------------------------------------------------------------------ Transcript

@Composable
fun TranscriptPane(meeting: Meeting, onTranscribe: () -> Unit, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(meeting.id, editing) { mutableStateOf(meeting.transcript.orEmpty()) }
    val transcript = meeting.transcript

    if (transcript.isNullOrBlank() && !editing) {
        EmptyState(
            icon = Icons.Default.RecordVoiceOver,
            title = if (meeting.status == MeetingStatus.TRANSCRIBING) "Transcribing…" else "No transcript yet",
            message = if (meeting.status == MeetingStatus.TRANSCRIBING) "The text will appear here as soon as it is ready."
            else "Convert the recording to text, or type/paste a transcript yourself.",
            actionLabel = if (meeting.status.isBusy) null else "Transcribe recording",
            onAction = onTranscribe,
            secondaryLabel = if (meeting.status.isBusy) null else "Type it manually",
            onSecondary = { editing = true },
        )
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editing) "Editing transcript" else "${transcript.orEmpty().split(Regex("\\s+")).size} words",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
                Button(onClick = { onSave(draft); editing = false }) { Text("Save") }
            } else if (!meeting.status.isBusy) {
                TextButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                label = { Text("Transcript") },
            )
        } else {
            SelectionContainer {
                Text(transcript.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ------------------------------------------------------------------ Action items

@Composable
fun ActionsPane(
    actions: List<ActionItem>,
    onToggle: (ActionItem) -> Unit,
    onEdit: (ActionItem?) -> Unit,
    onDelete: (ActionItem) -> Unit,
    onCalendar: (ActionItem) -> Unit,
    onRemind: (ActionItem) -> Unit,
    onEmailOwners: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val done = actions.count { it.done }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (actions.isEmpty()) "No action points" else "$done of ${actions.size} completed",
                    style = MaterialTheme.typography.titleMedium,
                )
                val overdue = actions.count { it.isOverdue() }
                if (overdue > 0) Text("$overdue overdue", style = MaterialTheme.typography.labelMedium, color = Brand.Red)
            }
            if (actions.isNotEmpty()) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy action points") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share action points") }
            }
        }
        if (actions.isNotEmpty()) ProgressLine(done, actions.size)
        // Open items first, then by due date and priority.
        actions.sortedWith(compareBy<ActionItem>({ it.done }, { it.dueAt ?: Long.MAX_VALUE }, { it.priority.ordinal })).forEach { item ->
            ActionCard(
                item = item,
                onToggle = { onToggle(item) },
                onClick = { onEdit(item) },
                onEmail = { onRemind(item) },
                onCalendar = { onCalendar(item) },
                onDelete = { onDelete(item) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
            if (actions.any { !it.done }) {
                Button(onClick = onEmailOwners) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Email owners")
                }
            }
        }
        Text(
            "Tap an action point to set the owner's email, a due date and priority. \"Remind\" opens Gmail with a ready reminder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------ Notes & tags

@Composable
fun NotesPane(meeting: Meeting, onSave: (notes: String, tags: String) -> Unit) {
    var notes by rememberSaveable(meeting.id) { mutableStateOf(meeting.notes) }
    var tags by rememberSaveable(meeting.id) { mutableStateOf(meeting.tags.joinToString(", ")) }
    val changed = remember(notes, tags, meeting.notes, meeting.tags) {
        notes != meeting.notes || tags.split(',').map { it.trim() }.filter { it.isNotEmpty() } != meeting.tags
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Notes", Icons.Default.EditNote)
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            placeholder = { Text("Add your own notes, context or corrections…") },
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tags") },
            placeholder = { Text("e.g. weekly review, Kano, budget") },
            supportingText = { Text("Separate tags with commas") },
            singleLine = true,
        )
        Button(onClick = { onSave(notes, tags) }, enabled = changed) { Text("Save notes") }
    }
}
