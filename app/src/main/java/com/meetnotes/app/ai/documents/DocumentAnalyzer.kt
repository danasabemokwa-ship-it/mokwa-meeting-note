package com.meetnotes.app.ai.documents

import android.util.Base64
import com.meetnotes.app.data.prefs.ApiProvider
import com.meetnotes.app.data.prefs.SecureKeyStore
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.data.remote.AnthropicApi
import com.meetnotes.app.data.remote.AnthropicMessage
import com.meetnotes.app.data.remote.AnthropicRequest
import com.meetnotes.app.data.remote.GeminiApi
import com.meetnotes.app.data.remote.GeminiContent
import com.meetnotes.app.data.remote.GeminiGenerationConfig
import com.meetnotes.app.data.remote.GeminiInlineData
import com.meetnotes.app.data.remote.GeminiPart
import com.meetnotes.app.data.remote.GeminiRequest
import com.meetnotes.app.data.remote.OpenAiApi
import com.meetnotes.app.data.remote.OpenAiChatRequest
import com.meetnotes.app.data.remote.OpenAiMessage
import com.meetnotes.app.data.remote.apiCall
import com.meetnotes.app.data.remote.text
import com.meetnotes.app.domain.model.DocKind
import com.meetnotes.app.domain.model.DocumentSummary
import com.meetnotes.app.domain.model.SummarizerType
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces the key points of an imported document with the AI engine chosen in Settings
 * ("Minutes writer"). Falls back to offline extraction when no key is set or the call fails,
 * so the user always gets a result.
 */
@Singleton
class DocumentAnalyzer @Inject constructor(
    private val settings: SettingsRepository,
    private val keys: SecureKeyStore,
    private val openAi: OpenAiApi,
    private val gemini: GeminiApi,
    private val anthropic: AnthropicApi,
) {

    suspend fun analyse(fileName: String, file: File, doc: ExtractedDocument, kind: DocKind): DocumentSummary {
        val s = settings.current()
        val glossary = s.glossary.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val offline = OfflineDocumentSummarizer.summarize(fileName, doc, kind)
        val noText = doc.wordCount < 20

        val engine = s.summarizer
        if (engine == SummarizerType.OFFLINE_RULES) {
            return if (noText && kind == DocKind.PDF) offline.copy(
                overview = "This PDF has no text layer (it is probably scanned). Add a Gemini API key in Settings and set " +
                    "\"Minutes writer\" to Gemini — it can read scanned pages.",
            ) else offline
        }

        return try {
            val system = DocumentPrompts.system(glossary)
            val stats = OfflineDocumentSummarizer.statisticsForPrompt(doc.tables)
            val user = DocumentPrompts.user(fileName, doc.text, stats)
            when (engine) {
                SummarizerType.GEMINI -> {
                    val key = keys.get(ApiProvider.GEMINI) ?: error("No Gemini key")
                    val parts = if (noText && kind == DocKind.PDF && file.length() <= MAX_INLINE_BYTES) {
                        listOf(
                            GeminiPart(inlineData = GeminiInlineData("application/pdf", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))),
                            GeminiPart(text = DocumentPrompts.userForAttachedFile(fileName)),
                        )
                    } else listOf(GeminiPart(text = user))
                    val r = apiCall("Gemini") {
                        gemini.generate(
                            model = s.geminiModel,
                            apiKey = key,
                            body = GeminiRequest(
                                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = system))),
                                contents = listOf(GeminiContent(role = "user", parts = parts)),
                                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json"),
                            ),
                        )
                    }
                    DocumentSummaryParser.parse(r.text()).copy(generatedBy = "Gemini · ${s.geminiModel}")
                }
                SummarizerType.OPENAI -> {
                    if (noText) return offline
                    val key = keys.get(ApiProvider.OPENAI) ?: error("No OpenAI key")
                    val r = apiCall("OpenAI") {
                        openAi.chat(
                            "Bearer $key",
                            OpenAiChatRequest(
                                model = s.openAiModel,
                                messages = listOf(OpenAiMessage("system", system), OpenAiMessage("user", user)),
                            ),
                        )
                    }
                    val content = r.choices.firstOrNull()?.message?.content ?: error("OpenAI returned no content")
                    DocumentSummaryParser.parse(content).copy(generatedBy = "OpenAI · ${s.openAiModel}")
                }
                SummarizerType.ANTHROPIC -> {
                    if (noText) return offline
                    val key = keys.get(ApiProvider.ANTHROPIC) ?: error("No Anthropic key")
                    val r = apiCall("Anthropic") {
                        anthropic.messages(
                            apiKey = key,
                            version = "2023-06-01",
                            body = AnthropicRequest(
                                model = s.anthropicModel,
                                system = system,
                                messages = listOf(AnthropicMessage("user", user)),
                            ),
                        )
                    }
                    val text = r.content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
                    DocumentSummaryParser.parse(text).copy(generatedBy = "Claude · ${s.anthropicModel}")
                }
                SummarizerType.OFFLINE_RULES -> offline
            }.let { ai ->
                // Exact spreadsheet statistics always win over the model's own numbers.
                if (kind == DocKind.EXCEL && offline.keyFigures.isNotEmpty()) ai.copy(keyFigures = offline.keyFigures) else ai
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            offline.copy(generatedBy = "Offline extraction (AI unavailable: ${e.message?.lineSequence()?.firstOrNull()?.take(120) ?: "error"})")
        }
    }

    companion object {
        /** Gemini accepts inline files up to ~20 MB per request (base64 adds a third). */
        private const val MAX_INLINE_BYTES = 14L * 1024 * 1024
    }
}
