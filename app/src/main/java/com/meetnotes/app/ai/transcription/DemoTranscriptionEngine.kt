package com.meetnotes.app.ai.transcription

import com.meetnotes.app.domain.model.TranscriptionEngineType
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake engine so the whole pipeline (transcript → minutes → action items → export) can be tried
 * without a model or API key. It ignores the audio and returns a sample meeting.
 */
@Singleton
class DemoTranscriptionEngine @Inject constructor() : TranscriptionEngine {
    override val type = TranscriptionEngineType.DEMO
    override val setupHint = ""
    override suspend fun isAvailable() = true

    override suspend fun transcribe(audio: File, options: TranscriptionOptions, onProgress: ProgressCallback): String {
        val lines = SAMPLE.lines()
        val sb = StringBuilder()
        lines.forEachIndexed { i, line ->
            delay(150)
            sb.appendLine(line)
            onProgress((i + 1f) / lines.size, sb.toString().trim())
        }
        return sb.toString().trim()
    }

    companion object {
        val SAMPLE = """
            [Demo transcript — choose a real transcription engine in Settings]
            Amina: Good morning everyone, let's start the weekly programme review. Musa, Grace and David are here.
            Musa: Report timeliness improved to 87 percent this month, up from 79 percent, but two facilities are still submitting late.
            Amina: That's good progress. What is causing the delays at those two sites?
            Musa: Mostly network problems and one new focal person who has not been trained yet.
            Grace: I'll schedule a refresher training for the new focal persons by next Tuesday.
            David: The supplier has not confirmed delivery of the data collection tablets. The budget allows for ten more units.
            Amina: We agreed last month to prioritise the two late sites, so they should receive tablets first.
            David: Understood. David needs to follow up with the supplier by 15 October and confirm the delivery date.
            Musa: Musa will send the updated timeliness report to the state team by Friday.
            Amina: We have decided to move this review meeting to Thursdays at 10 am starting next week.
            Grace: Should we also invite the facility focal persons to the next meeting?
            Amina: Yes, good idea. Grace should send the invitations once the training is done.
            Ngozi: Abeg, I go call the LGA team next tomorrow and revert by close of business Monday.
            Amina: The house agreed that the monthly report should be submitted latest by Friday.
            Amina: Next meeting we will review the training outcome and the tablet delivery. Thank you all.
        """.trimIndent()
    }
}
