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
import androidx.compose.foundation.layout.heightIn
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
import com.meetnotes.app.ui.components.BulletList
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.SectionTitle
import com.meetnotes.app.util.Formatters

// ------------------------------------------------------------------ Summary / minutes

@Composable
fun SummaryPane(
    meeting: Meeting,
    defaultTone: SummaryTone,
    actionCount: Int,
    onGenerate: (SummaryTone) -> Unit,
    onEdit: () -> Unit,
    onDownload: (ExportFormat) -> Unit,
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
            } else "Generate structured minutes: discussion points, decisions, action items and next steps.",
        )
        if (canGenerate) ToneAndGenerate(tone, { tone = it }, "Generate minutes") { onGenerate(tone) }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Text(summary.title.ifBlank { meeting.title }, style = MaterialTheme.typography.headlineSmall)
        Text(
            summary.dateTime.ifBlank { Formatters.dateTime(meeting.createdAt) } + " · " + Formatters.duration(meeting.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Participants", Icons.Default.Groups)
        if (summary.participants.isEmpty()) {
            Text("Not detected — tap Edit to add names.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.participants.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
        }

        SectionTitle("Key Discussion Points", Icons.Default.RecordVoiceOver)
        BulletList(summary.keyPoints)

        SectionTitle("Decisions Made", Icons.Default.Gavel)
        BulletList(summary.decisions)

        SectionTitle("Action Items", Icons.Default.Checklist)
        Text(
            if (actionCount == 0) "None recorded." else "$actionCount action item${if (actionCount == 1) "" else "s"} — see the Actions tab to track them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Next Steps / Follow-up", Icons.Default.Flag)
        BulletList(summary.nextSteps)

        if (summary.generatedBy.isNotBlank()) {
            Text(
                "Generated by ${summary.generatedBy}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onDownload(ExportFormat.PDF) }, enabled = !busy) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download PDF")
            }
            Button(onClick = { onDownload(ExportFormat.WORD) }, enabled = !busy) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download Word")
            }
            OutlinedButton(onClick = onEdit, enabled = !busy) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit minutes")
            }
        }
        if (canGenerate) {
            Spacer(Modifier.padding(top = 12.dp))
            ToneAndGenerate(tone, { tone = it }, "Regenerate") { onGenerate(tone) }
            Text(
                "Regenerating replaces the minutes and the action-item checklist.",
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
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val done = actions.count { it.done }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (actions.isEmpty()) "No action items" else "$done of ${actions.size} completed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (actions.isNotEmpty()) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy action items") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share action items") }
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
        actions.forEach { item ->
            ActionItemRow(item, onToggle, onEdit, onDelete, onCalendar)
            Spacer(Modifier.padding(top = 8.dp))
        }
        OutlinedButton(onClick = { onEdit(null) }, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add action item")
        }
    }
}

@Composable
private fun ActionItemRow(
    item: ActionItem,
    onToggle: (ActionItem) -> Unit,
    onEdit: (ActionItem?) -> Unit,
    onDelete: (ActionItem) -> Unit,
    onCalendar: (ActionItem) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.done) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth().clickable { onEdit(item) },
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle(item) })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    item.task,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Owner: ${item.owner}  ·  Due: ${item.dueDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { onCalendar(item) }) { Icon(Icons.Default.Event, contentDescription = "Add to calendar") }
            IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete action item") }
        }
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
