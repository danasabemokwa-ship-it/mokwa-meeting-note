package com.meetnotes.app.ui.actions

import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.ActionWithMeeting
import com.meetnotes.app.domain.model.Priority
import com.meetnotes.app.domain.model.startOfToday
import com.meetnotes.app.domain.repository.MeetingRepository
import com.meetnotes.app.export.GmailComposer
import com.meetnotes.app.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ActionFilter(val label: String) {
    OPEN("Open"), OVERDUE("Overdue"), WEEK("Due this week"), HIGH("High priority"), DONE("Completed"), ALL("All")
}

data class ActionsUiState(
    val loading: Boolean = true,
    val items: List<ActionWithMeeting> = emptyList(),
    val filter: ActionFilter = ActionFilter.OPEN,
    val query: String = "",
    val counts: Map<ActionFilter, Int> = emptyMap(),
    val total: Int = 0,
)

sealed interface ActionsEvent {
    data class Launch(val intent: Intent) : ActionsEvent
    data class Message(val text: String) : ActionsEvent
}

/** Every action point from every meeting in one tracker. */
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repo: MeetingRepository,
    private val gmail: GmailComposer,
) : ViewModel() {

    private val filter = MutableStateFlow(ActionFilter.OPEN)
    private val query = MutableStateFlow("")

    val state: StateFlow<ActionsUiState> = combine(repo.observeAllActions(), filter, query) { all, f, q ->
        val now = System.currentTimeMillis()
        val searched = all.filter { a ->
            q.isBlank() || a.item.task.contains(q, true) || a.item.owner.contains(q, true) || a.meetingTitle.contains(q, true)
        }
        ActionsUiState(
            loading = false,
            items = searched.filter { it.matches(f, now) },
            filter = f,
            query = q,
            counts = ActionFilter.entries.associateWith { ff -> searched.count { it.matches(ff, now) } },
            total = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionsUiState())

    private val _events = Channel<ActionsEvent>(Channel.BUFFERED)
    val events: Flow<ActionsEvent> = _events.receiveAsFlow()

    fun setFilter(f: ActionFilter) { filter.value = f }
    fun setQuery(q: String) { query.value = q }

    fun toggle(item: ActionItem) = viewModelScope.launch { repo.setActionDone(item.id, !item.done) }

    fun save(item: ActionItem) = viewModelScope.launch {
        if (item.task.isNotBlank()) repo.upsertActionItem(item)
    }

    fun delete(item: ActionItem) = viewModelScope.launch {
        repo.deleteActionItem(item.id)
        _events.send(ActionsEvent.Message("Action point deleted"))
    }

    fun remind(a: ActionWithMeeting) = viewModelScope.launch {
        _events.send(ActionsEvent.Launch(gmail.singleActionEmail(a.meetingTitle, a.meetingDate, a.item)))
    }

    /** One Gmail message listing every open action point grouped by owner (all meetings). */
    fun emailOpenSummary() = viewModelScope.launch {
        val open = state.value.items.filter { !it.item.done }
        if (open.isEmpty()) { _events.send(ActionsEvent.Message("No open action points in this view")); return@launch }
        _events.send(ActionsEvent.Launch(gmail.trackerEmail(open)))
    }

    fun calendar(a: ActionWithMeeting) = viewModelScope.launch {
        val item = a.item
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, item.task)
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Owner: ${item.owner}\nPriority: ${item.priority.label}\nFrom meeting: ${a.meetingTitle} (${Formatters.date(a.meetingDate)})",
            )
        item.dueAt?.let { start ->
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 86_400_000L)
        }
        _events.send(ActionsEvent.Launch(intent))
    }

    private fun ActionWithMeeting.matches(f: ActionFilter, now: Long): Boolean {
        val i = item
        return when (f) {
            ActionFilter.OPEN -> !i.done
            ActionFilter.OVERDUE -> i.isOverdue(now)
            ActionFilter.WEEK -> !i.done && i.dueAt != null && i.dueAt < startOfToday(now) + 7 * 86_400_000L
            ActionFilter.HIGH -> !i.done && i.priority == Priority.HIGH
            ActionFilter.DONE -> i.done
            ActionFilter.ALL -> true
        }
    }
}
