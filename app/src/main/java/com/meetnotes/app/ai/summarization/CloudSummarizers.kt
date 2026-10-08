package com.meetnotes.app.ai.summarization

import com.meetnotes.app.data.prefs.ApiProvider
import com.meetnotes.app.data.prefs.SecureKeyStore
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.data.remote.AnthropicApi
import com.meetnotes.app.data.remote.AnthropicMessage
import com.meetnotes.app.data.remote.AnthropicRequest
import com.meetnotes.app.data.remote.GeminiApi
import com.meetnotes.app.data.remote.GeminiContent
import com.meetnotes.app.data.remote.GeminiGenerationConfig
import com.meetnotes.app.data.remote.GeminiPart
import com.meetnotes.app.data.remote.GeminiRequest
import com.meetnotes.app.data.remote.OpenAiApi
import com.meetnotes.app.data.remote.OpenAiChatRequest
import com.meetnotes.app.data.remote.OpenAiMessage
import com.meetnotes.app.data.remote.apiCall
import com.meetnotes.app.data.remote.text
import com.meetnotes.app.domain.model.MinutesSummary
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.domain.model.SummaryTone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiSummarizer @Inject constructor(
    private val api: OpenAiApi,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
) : Summarizer {
    override val type = SummarizerType.OPENAI
    override val setupHint = "Add your OpenAI API key in Settings → API keys."
    override suspend fun isAvailable() = keys.has(ApiProvider.OPENAI)

    override suspend fun summarize(transcript: String, meta: MeetingMeta, tone: SummaryTone): MinutesSummary {
        val key = keys.get(ApiProvider.OPENAI) ?: error(setupHint)
        val model = settings.current().openAiModel
        val response = apiCall("OpenAI") {
            api.chat(
                "Bearer $key",
                OpenAiChatRequest(
                    model = model,
                    messages = listOf(
                        OpenAiMessage("system", MinutesPrompts.system(tone, meta)),
                        OpenAiMessage("user", MinutesPrompts.user(transcript, meta)),
                    ),
                ),
            )
        }
        val content = response.choices.firstOrNull()?.message?.content ?: error("OpenAI returned no content")
        return MinutesParser.parse(content).copy(generatedBy = "OpenAI · $model")
    }
}

@Singleton
class GeminiSummarizer @Inject constructor(
    private val api: GeminiApi,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
) : Summarizer {
    override val type = SummarizerType.GEMINI
    override val setupHint = "Add your Gemini API key in Settings → API keys."
    override suspend fun isAvailable() = keys.has(ApiProvider.GEMINI)

    override suspend fun summarize(transcript: String, meta: MeetingMeta, tone: SummaryTone): MinutesSummary {
        val key = keys.get(ApiProvider.GEMINI) ?: error(setupHint)
        val model = settings.current().geminiModel
        val response = apiCall("Gemini") {
            api.generate(
                model = model,
                apiKey = key,
                body = GeminiRequest(
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = MinutesPrompts.system(tone, meta)))),
                    contents = listOf(
                        GeminiContent(role = "user", parts = listOf(GeminiPart(text = MinutesPrompts.user(transcript, meta))))
                    ),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json"),
                ),
            )
        }
        return MinutesParser.parse(response.text()).copy(generatedBy = "Gemini · $model")
    }
}

@Singleton
class AnthropicSummarizer @Inject constructor(
    private val api: AnthropicApi,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
) : Summarizer {
    override val type = SummarizerType.ANTHROPIC
    override val setupHint = "Add your Anthropic API key in Settings → API keys."
    override suspend fun isAvailable() = keys.has(ApiProvider.ANTHROPIC)

    override suspend fun summarize(transcript: String, meta: MeetingMeta, tone: SummaryTone): MinutesSummary {
        val key = keys.get(ApiProvider.ANTHROPIC) ?: error(setupHint)
        val model = settings.current().anthropicModel
        val response = apiCall("Anthropic") {
            api.messages(
                apiKey = key,
                version = "2023-06-01",
                body = AnthropicRequest(
                    model = model,
                    system = MinutesPrompts.system(tone, meta),
                    messages = listOf(AnthropicMessage("user", MinutesPrompts.user(transcript, meta))),
                ),
            )
        }
        val text = response.content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
        return MinutesParser.parse(text).copy(generatedBy = "Claude · $model")
    }
}
