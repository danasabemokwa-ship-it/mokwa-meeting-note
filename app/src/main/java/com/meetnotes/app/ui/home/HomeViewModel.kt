package com.meetnotes.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetnotes.app.audio.RecordingState
import com.meetnotes.app.audio.RecordingStateHolder
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.data.repository.DocumentRepository
import com.meetnotes.app.domain.model.ActionWithMeeting
import com.meetnotes.app.domain.model.AppSettings
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.domain.model.TranscriptionEngineType
import com.meetnotes.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class DateFilter(val label: String) {
    ALL("All"), TODAY("Today"), WEEK("Last 7 days"), MONTH("Last 30 days")
}

/** Action progress shown on each meeting card. */
data class ActionProgress(val done: Int, val total: Int)

data class HomeUiState(
    val meetings: List<Meeting> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val dateFilter: DateFilter = DateFilter.ALL,
    val recording: RecordingState = RecordingState(),
    val loading: Boolean = true,
    val userName: String = "",
    val demoMode: Boolean = false,
    val openActions: Int = 0,
    val overdueActions: Int = 0,
    val documentCount: Int = 0,
    val progress: Map<Long, ActionProgress> = emptyMap(),
)

private data class Stats(
    val settings: AppSettings,
    val actions: List<ActionWithMeeting>,
    val documents: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: MeetingRepository,
    recordingState: RecordingStateHolder,
    settings: SettingsRepository,
    documents: DocumentRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val dateFilter = MutableStateFlow(DateFilter.ALL)

    private val stats = combine(
        settings.settings,
        repo.observeAllActions(),
        documents.observeAll().map { it.size },
    ) { s, a, d -> Stats(s, a, d) }

    val state: StateFlow<HomeUiState> = combine(
        repo.observeMeetings(), query, dateFilter, recordingState.state, stats,
    ) { meetings, q, filter, rec, st ->
        val now = System.currentTimeMillis()
        HomeUiState(
            meetings = meetings.filter { it.matches(q) && it.isIn(filter) },
            totalCount = meetings.size,
            query = q,
            dateFilter = filter,
            recording = rec,
            loading = false,
            userName = st.settings.userName,
            demoMode = st.settings.transcriptionEngine == TranscriptionEngineType.DEMO,
            openActions = st.actions.count { !it.item.done },
            overdueActions = st.actions.count { it.item.isOverdue(now) },
            documentCount = st.documents,
            progress = st.actions.groupBy { it.item.meetingId }
                .mapValues { (_, list) -> ActionProgress(list.count { it.item.done }, list.size) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onFilterChange(value: DateFilter) { dateFilter.value = value }

    private fun Meeting.matches(q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim()
        return title.contains(needle, true) ||
            tags.any { it.contains(needle, true) } ||
            notes.contains(needle, true) ||
            (transcript?.contains(needle, true) == true) ||
            (summary?.title?.contains(needle, true) == true) ||
            (summary?.keyPoints?.any { it.contains(needle, true) } == true) ||
            (summary?.participants?.any { it.contains(needle, true) } == true)
    }

    private fun Meeting.isIn(filter: DateFilter): Boolean {
        if (filter == DateFilter.ALL) return true
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (filter) {
            DateFilter.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -6)
            DateFilter.MONTH -> cal.add(Calendar.DAY_OF_YEAR, -29)
            else -> Unit
        }
        return createdAt >= cal.timeInMillis
    }
}
