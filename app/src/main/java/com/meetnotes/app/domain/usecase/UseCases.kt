package com.meetnotes.app.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meetnotes.app.ai.NigerianSpeech
import com.meetnotes.app.ai.summarization.MeetingMeta
import com.meetnotes.app.ai.summarization.SummarizerFactory
import com.meetnotes.app.ai.transcription.TranscriptionEngineFactory
import com.meetnotes.app.ai.transcription.TranscriptionOptions
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.ProcessMode
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.repository.MeetingRepository
import com.meetnotes.app.util.Formatters
import com.meetnotes.app.work.ProcessMeetingWorker
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject

/** Enqueues transcription and/or summarization as reliable background work. */
class ScheduleProcessingUseCase @Inject constructor(
    private val workManager: WorkManager,
) {
    operator fun invoke(meetingId: Long, mode: ProcessMode = ProcessMode.ALL, tone: SummaryTone? = null) {
        val request = OneTimeWorkRequestBuilder<ProcessMeetingWorker>()
            .setInputData(
                workDataOf(
                    ProcessMeetingWorker.KEY_MEETING_ID to meetingId,
                    ProcessMeetingWorker.KEY_MODE to mode.name,
                    ProcessMeetingWorker.KEY_TONE to tone?.name,
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(ProcessMeetingWorker.TAG)
            .build()
        workManager.enqueueUniqueWork("process_$meetingId", ExistingWorkPolicy.REPLACE, request)
    }
}

class TranscribeMeetingUseCase @Inject constructor(
    private val repo: MeetingRepository,
    private val engines: TranscriptionEngineFactory,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(meetingId: Long) {
        val meeting = repo.getMeeting(meetingId) ?: error("Meeting not found")
        val audio = File(meeting.audioPath)
        check(audio.exists()) { "The audio file for this meeting is missing." }

        val s = settings.current()
        val engine = engines.get(s.transcriptionEngine)
        check(engine.isAvailable()) { "${engine.type.label} is not set up. ${engine.setupHint}" }

        repo.setStatus(meetingId, MeetingStatus.TRANSCRIBING, 0f)
        val options = TranscriptionOptions(
            language = s.language,
            nigerian = s.nigerianSpeech,
            glossary = NigerianSpeech.parseGlossary(s.glossary),
        )
        val text = engine.transcribe(audio, options) { progress, partial ->
            repo.setStatus(meetingId, MeetingStatus.TRANSCRIBING, progress)
            if (partial != null) repo.updateTranscript(meetingId, partial)
        }
        check(text.isNotBlank()) { "No speech was detected in the recording." }
        repo.updateTranscript(meetingId, text)
        repo.setStatus(meetingId, MeetingStatus.TRANSCRIBED)
    }
}

class SummarizeMeetingUseCase @Inject constructor(
    private val repo: MeetingRepository,
    private val summarizers: SummarizerFactory,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(meetingId: Long, tone: SummaryTone? = null) {
        val meeting = repo.getMeeting(meetingId) ?: error("Meeting not found")
        val transcript = meeting.transcript?.takeIf { it.isNotBlank() }
            ?: error("Transcribe the meeting before generating minutes.")

        val s = settings.current()
        val chosenTone = tone ?: s.defaultTone
        val meta = MeetingMeta(
            title = meeting.title,
            dateTime = Formatters.dateTime(meeting.createdAt),
            knownParticipants = meeting.summary?.participants.orEmpty(),
            nigerian = s.nigerianSpeech,
            glossary = NigerianSpeech.parseGlossary(s.glossary),
        )
        repo.setStatus(meetingId, MeetingStatus.SUMMARIZING)

        val chosen = summarizers.get(s.summarizer)
        var note: String? = null
        val summary = try {
            check(chosen.isAvailable()) { "${chosen.type.label} is not set up. ${chosen.setupHint}" }
            chosen.summarize(transcript, meta, chosenTone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline-first: never leave the user without minutes because a cloud call failed.
            if (s.summarizer == SummarizerType.OFFLINE_RULES) throw e
            note = "Used offline extraction because ${chosen.type.label} failed: ${e.message ?: e.javaClass.simpleName}"
            summarizers.offline.summarize(transcript, meta, chosenTone)
        }

        val final = summary.copy(
            title = summary.title.ifBlank { meeting.title },
            dateTime = summary.dateTime.ifBlank { meta.dateTime },
        )
        repo.updateSummary(meetingId, final, replaceActions = true)
        if (Formatters.isAutoTitle(meeting.title) && !Formatters.isAutoTitle(final.title)) {
            repo.rename(meetingId, final.title.take(80))
        }
        repo.setStatus(meetingId, MeetingStatus.SUMMARIZED, null, note)
    }
}
