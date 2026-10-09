package com.meetnotes.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MeetingStatus {
    RECORDED, TRANSCRIBING, TRANSCRIBED, SUMMARIZING, SUMMARIZED, FAILED;

    val isBusy: Boolean get() = this == TRANSCRIBING || this == SUMMARIZING

    val label: String
        get() = when (this) {
            RECORDED -> "Recorded"
            TRANSCRIBING -> "Transcribing…"
            TRANSCRIBED -> "Transcribed"
            SUMMARIZING -> "Summarizing…"
            SUMMARIZED -> "Summarized"
            FAILED -> "Needs attention"
        }
}

data class Meeting(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val audioPath: String,
    val status: MeetingStatus,
    val transcript: String?,
    val summary: MinutesSummary?,
    val notes: String,
    val tags: List<String>,
    /** Error (status FAILED) or an informational note (e.g. "used offline fallback"). */
    val errorMessage: String?,
    /** 0..1 while a job reports progress, otherwise null. */
    val progress: Float?,
)

enum class Priority(val label: String) {
    HIGH("High"), MEDIUM("Medium"), LOW("Low");

    companion object {
        fun parse(raw: String?): Priority = when (raw?.trim()?.lowercase()) {
            "high", "urgent", "critical", "h" -> HIGH
            "low", "l" -> LOW
            else -> MEDIUM
        }
    }
}

/** Local midnight of the day containing [now]. */
fun startOfToday(now: Long = System.currentTimeMillis()): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

data class ActionItem(
    val id: Long = 0,
    val meetingId: Long,
    val owner: String = "TBD",
    val task: String,
    /** Deadline as said or typed ("Friday", "15 October", "TBD"). */
    val dueDate: String = "TBD",
    val done: Boolean = false,
    val position: Int = 0,
    val priority: Priority = Priority.MEDIUM,
    val ownerEmail: String = "",
    /** [dueDate] resolved to a real date (local midnight), when it could be understood. */
    val dueAt: Long? = null,
    val notes: String = "",
    val completedAt: Long? = null,
) {
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean =
        !done && dueAt != null && dueAt < startOfToday(now)

    fun isDueWithin(days: Int, now: Long = System.currentTimeMillis()): Boolean =
        !done && dueAt != null && dueAt >= startOfToday(now) && dueAt < startOfToday(now) + days * 86_400_000L
}

/** An action point together with the meeting it came from (for the all-meetings tracker). */
data class ActionWithMeeting(val item: ActionItem, val meetingTitle: String, val meetingDate: Long)

/**
 * Structured minutes. The JSON field names are the exact contract the LLM is asked to return
 * (see MinutesPrompts), so this class doubles as the parser target.
 */
@Serializable
data class MinutesSummary(
    @SerialName("meeting_title") val title: String = "",
    @SerialName("date_time") val dateTime: String = "",
    val participants: List<String> = emptyList(),
    @SerialName("key_discussion_points") val keyPoints: List<String> = emptyList(),
    @SerialName("decisions_made") val decisions: List<String> = emptyList(),
    @SerialName("action_items") val actionItems: List<SummaryActionItem> = emptyList(),
    @SerialName("next_steps") val nextSteps: List<String> = emptyList(),
    /** Filled in by the app (not the model): which engine produced this summary. */
    @SerialName("generated_by") val generatedBy: String = "",
)

@Serializable
data class SummaryActionItem(
    val owner: String = "TBD",
    val task: String = "",
    @SerialName("due_date") val dueDate: String = "TBD",
    /** "High", "Medium" or "Low". */
    val priority: String = "Medium",
)

fun MinutesSummary.normalized(): MinutesSummary {
    fun List<String>.clean() = map { it.trim().removePrefix("- ").removePrefix("• ").trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return copy(
        title = title.trim(),
        dateTime = dateTime.trim(),
        participants = participants.clean(),
        keyPoints = keyPoints.clean(),
        decisions = decisions.clean(),
        nextSteps = nextSteps.clean(),
        actionItems = actionItems
            .filter { it.task.isNotBlank() }
            .map {
                it.copy(
                    owner = it.owner.trim().ifBlank { "TBD" },
                    task = it.task.trim(),
                    dueDate = it.dueDate.trim().ifBlank { "TBD" },
                    priority = Priority.parse(it.priority).label,
                )
            },
    )
}

enum class SummaryTone(val label: String) {
    FORMAL("Formal"), CONCISE("Concise"), DETAILED("Detailed"), NIGERIAN_OFFICIAL("Nigerian official")
}

enum class TranscriptionEngineType(val label: String, val isCloud: Boolean) {
    DEMO("Demo (sample transcript)", false),
    LOCAL_WHISPER("On-device Whisper (offline)", false),
    OPENAI_WHISPER("OpenAI Whisper (cloud)", true),
    GEMINI("Google Gemini (cloud)", true),
}

enum class SummarizerType(val label: String, val isCloud: Boolean) {
    OFFLINE_RULES("Offline smart extraction", false),
    OPENAI("OpenAI GPT", true),
    GEMINI("Google Gemini", true),
    ANTHROPIC("Anthropic Claude", true),
}

enum class AudioQuality(val label: String, val sampleRate: Int, val bitRate: Int) {
    COMPACT("Compact · 16 kHz, 32 kbps (best for cloud, ~14 MB/hour)", 16_000, 32_000),
    STANDARD("Standard · 32 kHz, 64 kbps (~29 MB/hour)", 32_000, 64_000),
    HIGH("High · 44.1 kHz, 128 kbps (~58 MB/hour)", 44_100, 128_000),
}

enum class ProcessMode { ALL, TRANSCRIBE, SUMMARIZE }

data class AppSettings(
    val transcriptionEngine: TranscriptionEngineType = TranscriptionEngineType.DEMO,
    val summarizer: SummarizerType = SummarizerType.OFFLINE_RULES,
    /** ISO-639-1 code, "en-NG" (Nigerian English), "pcm" (Nigerian Pidgin) or "auto". */
    val language: String = "en-NG",
    /** Adds Nigerian accent / Pidgin / code-switching context to every engine. */
    val nigerianSpeech: Boolean = true,
    /** Comma-separated names, places and acronyms the engines should spell correctly. */
    val glossary: String = "",
    val defaultTone: SummaryTone = SummaryTone.FORMAL,
    val audioQuality: AudioQuality = AudioQuality.STANDARD,
    val autoProcess: Boolean = true,
    /** 0 = never delete. */
    val autoDeleteDays: Int = 0,
    val onboardingDone: Boolean = false,
    val openAiModel: String = "gpt-4o-mini",
    val geminiModel: String = "gemini-2.5-flash",
    val anthropicModel: String = "claude-haiku-5-5",
    /** Shown on the home screen and used to sign emails. */
    val userName: String = "",
    /** Organisation / designation, e.g. "WHO APHO, Kano". */
    val organisation: String = "",
    /** Comma-separated email addresses pre-filled when emailing minutes. */
    val defaultRecipients: String = "",
)
