@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.audio.RecStatus
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.RecordRed
import com.meetnotes.app.ui.components.StatusChip
import com.meetnotes.app.util.Formatters

@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Mokwa Meeting Note") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (!state.recording.isActive) {
                ExtendedFloatingActionButton(
                    onClick = onRecord,
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    text = { Text("New recording") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(visible = state.recording.isActive) {
                RecordingBanner(
                    paused = state.recording.status == RecStatus.PAUSED,
                    elapsedMs = state.recording.elapsedMs,
                    onClick = onRecord,
                )
            }

            if (state.totalCount > 0) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search titles, transcripts, tags…") },
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
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
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

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Default.Mic,
                    title = "No meetings yet",
                    message = "Record your first meeting. Mokwa Meeting Note will transcribe it and write the minutes and action points for you.",
                    actionLabel = "Start recording",
                    onAction = onRecord,
                    modifier = Modifier.fillMaxSize(),
                )
                state.meetings.isEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No matches",
                    message = "No meetings match your search or date filter.",
                    actionLabel = "Clear filters",
                    onAction = { vm.onQueryChange(""); vm.onFilterChange(DateFilter.ALL) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyVerticalGrid(
                    // One column on phones, two or more on tablets / landscape.
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.meetings, key = { it.id }) { meeting ->
                        MeetingCard(meeting, onClick = { onOpenMeeting(meeting.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBanner(paused: Boolean, elapsedMs: Long, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onClick),
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
private fun MeetingCard(meeting: Meeting, onClick: () -> Unit) {
    val openActions = meeting.summary?.actionItems?.size ?: 0
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(meeting.status)
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaItem(Icons.Default.Event, Formatters.dateTime(meeting.createdAt))
                Spacer(Modifier.width(16.dp))
                MetaItem(Icons.Default.Schedule, Formatters.duration(meeting.durationMs))
            }
            val preview = meeting.summary?.keyPoints?.firstOrNull() ?: meeting.transcript?.lineSequence()?.firstOrNull { !it.startsWith("[") }
            if (!preview.isNullOrBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (meeting.tags.isNotEmpty() || openActions > 0) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (meeting.tags.isNotEmpty()) MetaItem(Icons.Default.Sell, meeting.tags.joinToString(" · "))
                    if (openActions > 0) {
                        Spacer(Modifier.weight(1f))
                        Text("$openActions action item${if (openActions == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
