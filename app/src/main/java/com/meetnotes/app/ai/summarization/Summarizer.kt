package com.meetnotes.app.ai.summarization

import com.meetnotes.app.domain.model.MinutesSummary
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.domain.model.SummaryTone
import javax.inject.Inject
import javax.inject.Singleton

data class MeetingMeta(
    val title: String,
    val dateTime: String,
    val knownParticipants: List<String> = emptyList(),
    /** Interpret Nigerian English / Pidgin / code-switching. */
    val nigerian: Boolean = false,
    /** Names, places and acronyms to spell correctly. */
    val glossary: List<String> = emptyList(),
)

/** Abstraction over every minutes generator (offline rules or any LLM provider). */
interface Summarizer {
    val type: SummarizerType
    val setupHint: String
    suspend fun isAvailable(): Boolean
    suspend fun summarize(transcript: String, meta: MeetingMeta, tone: SummaryTone): MinutesSummary
}

@Singleton
class SummarizerFactory @Inject constructor(
    val offline: OfflineRuleSummarizer,
    private val openAi: OpenAiSummarizer,
    private val gemini: GeminiSummarizer,
    private val anthropic: AnthropicSummarizer,
) {
    fun get(type: SummarizerType): Summarizer = when (type) {
        SummarizerType.OFFLINE_RULES -> offline
        SummarizerType.OPENAI -> openAi
        SummarizerType.GEMINI -> gemini
        SummarizerType.ANTHROPIC -> anthropic
    }
}
