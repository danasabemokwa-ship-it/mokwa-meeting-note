package com.meetnotes.app.ui.detail

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.AppSettings
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.MinutesSummary
import com.meetnotes.app.domain.model.ProcessMode
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.model.normalized
import com.meetnotes.app.domain.repository.MeetingRepository
import com.meetnotes.app.domain.usecase.ScheduleProcessingUseCase
import com.meetnotes.app.export.ExportFormat
import com.meetnotes.app.export.ExportManager
import com.meetnotes.app.export.GmailComposer
import com.meetnotes.app.ui.components.EmailRequest
import com.meetnotes.app.export.MinutesFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val meeting: Meeting? = null,
    val actions: List<ActionItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

sealed interface DetailEvent {
    data class Launch(val intent: Intent) : DetailEvent
    data class Message(val text: String) : DetailEvent
    data class Copy(val text: String) : DetailEvent
    /** Ask the UI to open the system "Save as" dialog for [format]. */
    data class SaveAs(val format: ExportFormat, val suggestedName: String) : DetailEvent
    data object Deleted : DetailEvent
}

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MeetingRepository,
    private val scheduleProcessing: ScheduleProcessingUseCase,
    private val exporter: ExportManager,
    private val gmail: GmailComposer,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val meetingId: Long = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<DetailUiState> = combine(
        repo.observeMeeting(meetingId),
        repo.observeActionItems(meetingId),
        settingsRepository.settings,
    ) { meeting, actions, settings ->
        DetailUiState(loading = false, meeting = meeting, actions = actions, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    private val meeting get() = state.value.meeting

    val gmailInstalled: Boolean get() = gmail.isGmailInstalled()

    // ---------------------------------------------------------------- processing

    /** Transcribe the recording again (e.g. after switching from Demo to Gemini) and rewrite the minutes. */
    fun retranscribe() = scheduleProcessing(meetingId, ProcessMode.ALL)

    fun transcribe() = scheduleProcessing(meetingId, ProcessMode.TRANSCRIBE)

    fun generateMinutes(tone: SummaryTone) = scheduleProcessing(meetingId, ProcessMode.SUMMARIZE, tone)

    /** Retries whatever step is missing. */
    fun retry() {
        val m = meeting ?: return
        if (m.transcript.isNullOrBlank()) scheduleProcessing(meetingId, ProcessMode.ALL)
        else scheduleProcessing(meetingId, ProcessMode.SUMMARIZE)
    }

    fun dismissNote() = viewModelScope.launch {
        val m = meeting ?: return@launch
        repo.setStatus(meetingId, m.status, m.progress, null)
    }

    // ---------------------------------------------------------------- edits

    fun saveTranscript(text: String) = viewModelScope.launch {
        repo.updateTranscript(meetingId, text.trim())
        val m = meeting
        if (m != null && (m.status == MeetingStatus.RECORDED || m.status == MeetingStatus.FAILED)) {
            repo.setStatus(meetingId, MeetingStatus.TRANSCRIBED)
        }
        _events.send(DetailEvent.Message("Transcript saved"))
    }

    fun saveSummary(summary: MinutesSummary) = viewModelScope.launch {
        val normalized = summary.normalized()
        repo.updateSummary(meetingId, normalized, replaceActions = false)
        if (normalized.title.isNotBlank()) repo.rename(meetingId, normalized.title)
        if (meeting?.status != MeetingStatus.SUMMARIZED) repo.setStatus(meetingId, MeetingStatus.SUMMARIZED)
        _events.send(DetailEvent.Message("Minutes updated"))
    }

    fun rename(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        repo.rename(meetingId, title)
        meeting?.summary?.let { repo.updateSummary(meetingId, it.copy(title = title.trim()), replaceActions = false) }
    }

    fun saveNotes(notes: String, tagsText: String) = viewModelScope.launch {
        repo.updateNotesAndTags(meetingId, notes.trim(), tagsText.split(','))
        _events.send(DetailEvent.Message("Notes saved"))
    }

    fun delete() = viewModelScope.launch {
        repo.deleteMeeting(meetingId)
        _events.send(DetailEvent.Deleted)
    }

    // ---------------------------------------------------------------- action items

    fun toggleAction(item: ActionItem) = viewModelScope.launch {
        repo.setActionDone(item.id, !item.done)
    }

    fun saveAction(item: ActionItem) = viewModelScope.launch {
        if (item.task.isBlank()) return@launch
        repo.upsertActionItem(item.copy(meetingId = meetingId))
    }

    fun deleteAction(item: ActionItem) = viewModelScope.launch {
        repo.deleteActionItem(item.id)
        _events.send(DetailEvent.Message("Action item deleted"))
    }

    fun addToCalendar(item: ActionItem) = viewModelScope.launch {
        val m = meeting ?: return@launch
        _events.send(DetailEvent.Launch(exporter.calendarIntent(m, item)))
    }

    fun copyActionItems() = viewModelScope.launch {
        val m = meeting ?: return@launch
        _events.send(DetailEvent.Copy(MinutesFormatter.actionItemsText(m, state.value.actions)))
    }

    fun shareActionItems() = viewModelScope.launch {
        val m = meeting ?: return@launch
        _events.send(DetailEvent.Launch(exporter.shareText("Action items – ${m.title}", MinutesFormatter.actionItemsText(m, state.value.actions))))
    }

    // ---------------------------------------------------------------- Gmail

    fun emailMinutes(request: EmailRequest) = viewModelScope.launch {
        val m = meeting ?: return@launch
        runCatching { gmail.minutesEmail(m, state.value.actions, request.to, request.cc, request.attachment, request.includeTranscript) }
            .onSuccess { _events.send(DetailEvent.Launch(it)) }
            .onFailure { _events.send(DetailEvent.Message("Could not prepare the email: ${it.message}")) }
    }

    fun emailOwners() = viewModelScope.launch {
        val m = meeting ?: return@launch
        val open = state.value.actions.filter { !it.done }
        if (open.isEmpty()) { _events.send(DetailEvent.Message("All action points are done")); return@launch }
        if (open.none { it.ownerEmail.contains('@') }) {
            _events.send(DetailEvent.Message("Tip: add owners' emails (tap an action point) so Gmail fills in the recipients"))
        }
        _events.send(DetailEvent.Launch(gmail.actionOwnersEmail(m, state.value.actions)))
    }

    fun remind(item: ActionItem) = viewModelScope.launch {
        val m = meeting ?: return@launch
        _events.send(DetailEvent.Launch(gmail.singleActionEmail(m.summary?.title?.ifBlank { null } ?: m.title, m.createdAt, item)))
    }

    // ---------------------------------------------------------------- export / share

    fun export(format: ExportFormat, includeTranscript: Boolean) = viewModelScope.launch {
        val m = meeting ?: return@launch
        runCatching { exporter.export(m, state.value.actions, format, includeTranscript) }
            .onSuccess { _events.send(DetailEvent.Launch(it)) }
            .onFailure { _events.send(DetailEvent.Message("Export failed: ${it.message}")) }
    }

    private var pendingDownload: java.io.File? = null

    /** Step 1 of "Download": build the file, then ask the UI to show the Save-as dialog. */
    fun download(format: ExportFormat, includeTranscript: Boolean) = viewModelScope.launch {
        val m = meeting ?: return@launch
        runCatching { exporter.writeFile(m, state.value.actions, format, includeTranscript) }
            .onSuccess {
                pendingDownload = it.file
                _events.send(DetailEvent.SaveAs(format, it.file.name))
            }
            .onFailure { _events.send(DetailEvent.Message("Could not create the file: ${it.message}")) }
    }

    /** Step 2: the user picked a location (null = cancelled). */
    fun onSaveLocationChosen(uri: android.net.Uri?) = viewModelScope.launch {
        val file = pendingDownload ?: return@launch
        pendingDownload = null
        if (uri == null) return@launch
        runCatching { exporter.saveTo(file, uri) }
            .onSuccess { _events.send(DetailEvent.Message("Saved ${file.name}")) }
            .onFailure { _events.send(DetailEvent.Message("Save failed: ${it.message}")) }
    }

    fun shareAudio() = viewModelScope.launch {
        val m = meeting ?: return@launch
        val intent = exporter.shareAudio(m)
        _events.send(if (intent != null) DetailEvent.Launch(intent) else DetailEvent.Message("Audio file not found"))
    }
}
