@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.actions

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.domain.model.ActionWithMeeting
import com.meetnotes.app.ui.components.ActionCard
import com.meetnotes.app.ui.components.ActionEditorDialog
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.GradientHeader
import com.meetnotes.app.ui.components.ProgressLine
import com.meetnotes.app.util.Formatters

@Composable
fun ActionsScreen(
    onOpenMeeting: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
    vm: ActionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ActionWithMeeting?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ActionsEvent.Launch -> try { context.startActivity(e.intent) } catch (x: ActivityNotFoundException) {
                    snackbar.showSnackbar("No app available for this")
                }
                is ActionsEvent.Message -> snackbar.showSnackbar(e.text)
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                val open = state.counts[ActionFilter.OPEN] ?: 0
                val done = state.counts[ActionFilter.DONE] ?: 0
                GradientHeader(
                    title = "Action points",
                    subtitle = "Across all meetings",
                    actions = {
                        IconButton(onClick = vm::emailOpenSummary) {
                            Icon(Icons.Default.Email, contentDescription = "Email open action points with Gmail", tint = Color.White)
                        }
                    },
                ) {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            "$open open · ${state.counts[ActionFilter.OVERDUE] ?: 0} overdue · $done done",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (open + done > 0) {
                            androidx.compose.material3.Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(top = 10.dp),
                            ) {
                                ProgressLine(done, open + done, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search task, person or meeting") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ActionFilter.entries) { f ->
                        val n = state.counts[f] ?: 0
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { vm.setFilter(f) },
                            label = { Text(if (n > 0) "${f.label} ($n)" else f.label) },
                        )
                    }
                }
            }
            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.items.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Default.TaskAlt,
                        title = if (state.total == 0) "No action points yet" else "Nothing here",
                        message = if (state.total == 0) "Action points from your meeting minutes will appear here so you can track them to completion."
                        else when (state.filter) {
                            ActionFilter.OVERDUE -> "Great — nothing is overdue."
                            ActionFilter.OPEN -> "All action points are completed. Well done!"
                            else -> "No action points match this filter."
                        },
                    )
                }
                else -> items(state.items, key = { it.item.id }) { a ->
                    ActionCard(
                        item = a.item,
                        onToggle = { vm.toggle(a.item) },
                        onClick = { editing = a },
                        meetingTitle = "${a.meetingTitle} · ${Formatters.date(a.meetingDate)}",
                        onMeetingClick = { onOpenMeeting(a.item.meetingId) },
                        onEmail = { vm.remind(a) },
                        onCalendar = { vm.calendar(a) },
                        onDelete = { vm.delete(a.item) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    editing?.let { a ->
        ActionEditorDialog(
            initial = a.item,
            meetingId = a.item.meetingId,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
        )
    }
}
