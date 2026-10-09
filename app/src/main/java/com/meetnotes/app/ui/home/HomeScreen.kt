@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.audio.RecStatus
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.ui.components.AvatarStack
import com.meetnotes.app.ui.components.DemoModeBanner
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.GradientHeader
import com.meetnotes.app.ui.components.ProgressLine
import com.meetnotes.app.ui.components.RecordRed
import com.meetnotes.app.ui.components.StatTile
import com.meetnotes.app.ui.components.StatusChip
import com.meetnotes.app.ui.theme.Brand
import com.meetnotes.app.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onSettings: () -> Unit,
    onActions: () -> Unit,
    onDocuments: () -> Unit,
    onAddDocument: () -> Unit,
    bottomBar: @Composable () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = bottomBar,
        floatingActionButton = {
            if (!state.recording.isActive) {
                ExtendedFloatingActionButton(
                    onClick = onRecord,
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    text = { Text("Record") },
                    containerColor = Brand.Green,
                    contentColor = Color.White,
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            // One column on phones, two or more on tablets / landscape.
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GradientHeader(
                    title = greeting(state.userName),
                    subtitle = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("${state.totalCount}", "Meetings", Icons.Default.EventNote, Brand.GreenLight,
                            Modifier.weight(1f))
                        StatTile("${state.openActions}", "Open actions", Icons.Default.Checklist, Brand.Gold,
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onActions))
                        StatTile("${state.overdueActions}", "Overdue", Icons.Default.WarningAmber,
                            if (state.overdueActions > 0) Brand.Red else Brand.Slate,
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onActions))
                    }
                }
            }

            if (state.recording.isActive) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecordingBanner(
                        paused = state.recording.status == RecStatus.PAUSED,
                        elapsedMs = state.recording.elapsedMs,
                        onClick = onRecord,
                    )
                }
            }

            if (state.demoMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DemoModeBanner(onFix = onSettings, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction(
                        icon = Icons.Default.Mic,
                        title = "Record meeting",
                        text = "Minutes & action points",
                        tint = Brand.Green,
                        onClick = onRecord,
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon = Icons.Default.NoteAdd,
                        title = "Summarise file",
                        text = if (state.documentCount > 0) "${state.documentCount} document${if (state.documentCount == 1) "" else "s"}" else "Word · PDF · Excel",
                        tint = Brand.Blue,
                        onClick = if (state.documentCount > 0) onDocuments else onAddDocument,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.totalCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "Recent meetings",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
                        )
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = vm::onQueryChange,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            placeholder = { Text("Search meetings, people, topics…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { vm.onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(DateFilter.entries) { f ->
                                FilterChip(
                                    selected = state.dateFilter == f,
                                    onClick = { vm.onFilterChange(f) },
                                    label = { Text(f.label) },
                                )
                            }
                        }
                    }
                }
            }

            when {
                state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.totalCount == 0 -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Default.Mic,
                        title = "No meetings yet",
                        message = "Record your first meeting. Mokwa Meeting Note will transcribe it and write structured minutes and action points for you.",
                        actionLabel = "Start recording",
                        onAction = onRecord,
                    )
                }
                state.meetings.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        message = "No meetings match your search or date filter.",
                        actionLabel = "Clear filters",
                        onAction = { vm.onQueryChange(""); vm.onFilterChange(DateFilter.ALL) },
                    )
                }
                else -> items(state.meetings, key = { it.id }) { meeting ->
                    MeetingCard(
                        meeting,
                        progress = state.progress[meeting.id],
                        onClick = { onOpenMeeting(meeting.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 0..11 -> "Good morning"
        in 12..15 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = name.trim().split(' ').firstOrNull { it.isNotBlank() && !it.endsWith('.') }
    return if (first != null) "$part, $first" else part
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    text: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun RecordingBanner(paused: Boolean, elapsedMs: Long, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.RecordRed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (paused) "Recording paused" else "Recording in progress", style = MaterialTheme.typography.titleSmall)
                Text("Tap to return to the recorder", style = MaterialTheme.typography.bodySmall)
            }
            Text(Formatters.duration(elapsedMs), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MeetingCard(meeting: Meeting, progress: ActionProgress?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = meeting.createdAt }
    val title = meeting.summary?.title?.ifBlank { null } ?: meeting.title
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            // Calendar-style date block
            Column(
                Modifier
                    .width(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    SimpleDateFormat("MMM", Locale.getDefault()).format(Date(meeting.createdAt)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${cal.get(Calendar.DAY_OF_MONTH)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(meeting.status)
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(meeting.createdAt))} · ${Formatters.duration(meeting.durationMs)}" +
                            if (meeting.tags.isNotEmpty()) " · ${meeting.tags.joinToString(", ")}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val preview = meeting.summary?.keyPoints?.firstOrNull()
                    ?: meeting.transcript?.lineSequence()?.firstOrNull { it.isNotBlank() && !it.startsWith("[") }
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
                val people = meeting.summary?.participants.orEmpty()
                if (people.isNotEmpty() || (progress != null && progress.total > 0)) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (people.isNotEmpty()) {
                            AvatarStack(people, max = 4, size = 24.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        if (progress != null && progress.total > 0) {
                            ProgressLine(progress.done, progress.total, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
