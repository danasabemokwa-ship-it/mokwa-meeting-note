package com.meetnotes.app.ui.documents

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.data.repository.DocumentRepository
import com.meetnotes.app.domain.model.AppSettings
import com.meetnotes.app.domain.model.DocKind
import com.meetnotes.app.domain.model.DocumentItem
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.export.DocumentFormatter
import com.meetnotes.app.export.ExportFormat
import com.meetnotes.app.export.ExportManager
import com.meetnotes.app.export.GmailComposer
import com.meetnotes.app.ui.components.EmailRequest
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
import java.io.File
import javax.inject.Inject

sealed interface DocEvent {
    data class Launch(val intent: Intent) : DocEvent
    data class Message(val text: String) : DocEvent
    data class Copy(val text: String) : DocEvent
    data class Opened(val id: Long) : DocEvent
    data class SaveAs(val format: ExportFormat, val suggestedName: String) : DocEvent
    data object Deleted : DocEvent
}

data class DocumentsUiState(
    val loading: Boolean = true,
    val documents: List<DocumentItem> = emptyList(),
    val query: String = "",
    val kind: DocKind? = null,
    val usingAi: Boolean = false,
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repo: DocumentRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow<DocKind?>(null)

    val state: StateFlow<DocumentsUiState> = combine(repo.observeAll(), query, kind, settings.settings) { docs, q, k, s ->
        DocumentsUiState(
            loading = false,
            documents = docs.filter { d ->
                (k == null || d.kind == k) &&
                    (q.isBlank() || d.name.contains(q, true) || d.summary?.title?.contains(q, true) == true ||
                        d.summary?.keyPoints?.any { it.contains(q, true) } == true)
            },
            query = q,
            kind = k,
            usingAi = s.summarizer != SummarizerType.OFFLINE_RULES,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

    private val _events = Channel<DocEvent>(Channel.BUFFERED)
    val events: Flow<DocEvent> = _events.receiveAsFlow()

    fun setQuery(q: String) { query.value = q }
    fun setKind(k: DocKind?) { kind.value = k }

    fun importFile(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importFromUri(uri) }
            .onSuccess { _events.send(DocEvent.Opened(it)) }
            .onFailure { _events.send(DocEvent.Message(it.message ?: "Couldn't add this file")) }
    }

    fun importText(title: String, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        runCatching { repo.importText(title, text) }
            .onSuccess { _events.send(DocEvent.Opened(it)) }
            .onFailure { _events.send(DocEvent.Message(it.message ?: "Couldn't add the text")) }
    }
}

data class DocumentDetailUiState(
    val loading: Boolean = true,
    val document: DocumentItem? = null,
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DocumentRepository,
    private val exporter: ExportManager,
    private val gmail: GmailComposer,
    private val meetings: com.meetnotes.app.domain.repository.MeetingRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<DocumentDetailUiState> = combine(repo.observe(id), settings.settings) { d, s ->
        DocumentDetailUiState(loading = false, document = d, settings = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentDetailUiState())

    private val _events = Channel<DocEvent>(Channel.BUFFERED)
    val events: Flow<DocEvent> = _events.receiveAsFlow()

    val gmailInstalled: Boolean get() = gmail.isGmailInstalled()

    private val doc get() = state.value.document

    fun reanalyse() = viewModelScope.launch { repo.reanalyse(id) }

    fun rename(name: String) = viewModelScope.launch { if (name.isNotBlank()) repo.rename(id, name) }

    fun delete() = viewModelScope.launch {
        repo.delete(id)
        _events.send(DocEvent.Deleted)
    }

    fun email(request: EmailRequest) = viewModelScope.launch {
        val d = doc ?: return@launch
        runCatching { gmail.documentEmail(d, request.to, request.cc, request.attachment) }
            .onSuccess { _events.send(DocEvent.Launch(it)) }
            .onFailure { _events.send(DocEvent.Message("Could not prepare the email: ${it.message}")) }
    }

    fun share(format: ExportFormat) = viewModelScope.launch {
        val d = doc ?: return@launch
        runCatching { exporter.exportDocument(d, format) }
            .onSuccess { _events.send(DocEvent.Launch(it)) }
            .onFailure { _events.send(DocEvent.Message("Export failed: ${it.message}")) }
    }

    private var pending: File? = null

    fun download(format: ExportFormat) = viewModelScope.launch {
        val d = doc ?: return@launch
        runCatching { exporter.writeDocumentSummary(d, format) }
            .onSuccess { pending = it.file; _events.send(DocEvent.SaveAs(format, it.file.name)) }
            .onFailure { _events.send(DocEvent.Message("Could not create the file: ${it.message}")) }
    }

    fun onSaveLocationChosen(uri: Uri?) = viewModelScope.launch {
        val file = pending ?: return@launch
        pending = null
        if (uri == null) return@launch
        runCatching { exporter.saveTo(file, uri) }
            .onSuccess { _events.send(DocEvent.Message("Saved ${file.name}")) }
            .onFailure { _events.send(DocEvent.Message("Save failed: ${it.message}")) }
    }

    fun openOriginal() = viewModelScope.launch {
        val d = doc ?: return@launch
        val f = repo.originalFile(id)
        if (f == null) _events.send(DocEvent.Message("The original file is missing"))
        else _events.send(DocEvent.Launch(exporter.viewIntent(f, d.mimeType)))
    }

    fun copySummary() = viewModelScope.launch {
        val d = doc ?: return@launch
        _events.send(DocEvent.Copy(DocumentFormatter.plainText(d)))
    }

    fun copyText() = viewModelScope.launch {
        val t = repo.text(id)
        if (t.isNullOrBlank()) _events.send(DocEvent.Message("No text was extracted from this file"))
        else _events.send(DocEvent.Copy(t))
    }

    suspend fun fullText(): String = repo.text(id).orEmpty()

    /** Adds the document's action points to the Action tracker under a meeting named after it. */
    fun addActionsToTracker() = viewModelScope.launch {
        val d = doc ?: return@launch
        val items = d.summary?.actionItems.orEmpty()
        if (items.isEmpty()) { _events.send(DocEvent.Message("This document has no action points")); return@launch }
        val meetingId = meetings.createMeeting(
            title = "Document: ${DocumentFormatter.title(d)}",
            audioPath = "",
            durationMs = 0,
            createdAt = System.currentTimeMillis(),
        )
        val summary = com.meetnotes.app.domain.model.MinutesSummary(
            title = "Document: ${DocumentFormatter.title(d)}",
            keyPoints = d.summary?.keyPoints.orEmpty(),
            actionItems = items,
            nextSteps = d.summary?.recommendations.orEmpty(),
            generatedBy = d.summary?.generatedBy.orEmpty(),
        )
        meetings.updateSummary(meetingId, summary, replaceActions = true)
        meetings.setStatus(meetingId, com.meetnotes.app.domain.model.MeetingStatus.SUMMARIZED)
        _events.send(DocEvent.Message("${items.size} action point${if (items.size == 1) "" else "s"} added to the Actions tab"))
    }
}
