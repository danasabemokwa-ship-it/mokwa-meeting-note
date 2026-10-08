package com.meetnotes.app.ai.transcription

import android.util.Base64
import com.meetnotes.app.ai.NigerianSpeech
import com.meetnotes.app.data.prefs.ApiProvider
import com.meetnotes.app.data.prefs.SecureKeyStore
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.data.remote.GeminiApi
import com.meetnotes.app.data.remote.GeminiContent
import com.meetnotes.app.data.remote.GeminiGenerationConfig
import com.meetnotes.app.data.remote.GeminiInlineData
import com.meetnotes.app.data.remote.GeminiPart
import com.meetnotes.app.data.remote.GeminiRequest
import com.meetnotes.app.data.remote.OpenAiApi
import com.meetnotes.app.data.remote.apiCall
import com.meetnotes.app.data.remote.text
import com.meetnotes.app.data.prefs.LANGUAGES
import com.meetnotes.app.domain.model.TranscriptionEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MB = 1024L * 1024L

/** OpenAI Whisper API — accepts .m4a directly, max 25 MB per request. */
@Singleton
class OpenAiWhisperEngine @Inject constructor(
    private val api: OpenAiApi,
    private val keys: SecureKeyStore,
) : TranscriptionEngine {
    override val type = TranscriptionEngineType.OPENAI_WHISPER
    override val setupHint = "Add your OpenAI API key in Settings → API keys."
    override suspend fun isAvailable() = keys.has(ApiProvider.OPENAI)

    override suspend fun transcribe(audio: File, options: TranscriptionOptions, onProgress: ProgressCallback): String {
        val key = keys.get(ApiProvider.OPENAI) ?: error(setupHint)
        val language = NigerianSpeech.whisperLanguage(options.language)
        val prompt = NigerianSpeech.whisperPrompt(options.nigerian, options.glossary)
        require(audio.length() <= 25 * MB) {
            "Recording is ${audio.length() / MB} MB; OpenAI accepts up to 25 MB. " +
                "Use 'Compact' audio quality for long meetings, or on-device Whisper."
        }
        onProgress(0.1f, null)
        val part = MultipartBody.Part.createFormData(
            "file", audio.name, audio.asRequestBody("audio/mp4".toMediaType())
        )
        val result = apiCall("OpenAI") {
            api.transcribe(
                bearer = "Bearer $key",
                file = part,
                model = "whisper-1".toRequestBody(),
                language = language.takeIf { it != "auto" }?.toRequestBody(),
                prompt = prompt?.toRequestBody(),
                responseFormat = "json".toRequestBody(),
            )
        }
        onProgress(1f, result.text)
        return result.text.trim()
    }
}

/**
 * Google Gemini multimodal transcription. Audio is sent inline (base64), so the request must stay
 * under ~20 MB — roughly 80 minutes at 'Compact' quality.
 */
@Singleton
class GeminiTranscriptionEngine @Inject constructor(
    private val api: GeminiApi,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
) : TranscriptionEngine {
    override val type = TranscriptionEngineType.GEMINI
    override val setupHint = "Add your Gemini API key in Settings → API keys."
    override suspend fun isAvailable() = keys.has(ApiProvider.GEMINI)

    override suspend fun transcribe(audio: File, options: TranscriptionOptions, onProgress: ProgressCallback): String {
        val key = keys.get(ApiProvider.GEMINI) ?: error(setupHint)
        val language = options.language
        require(audio.length() <= 15 * MB) {
            "Recording is ${audio.length() / MB} MB; inline Gemini audio is limited to about 15 MB. " +
                "Use 'Compact' audio quality, or OpenAI / on-device Whisper."
        }
        onProgress(0.05f, null)
        val base64 = withContext(Dispatchers.IO) { Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP) }
        onProgress(0.2f, null)
        val languageName = LANGUAGES.firstOrNull { it.first == language }?.second
        val instruction = buildString {
            append("Transcribe this meeting recording verbatim. ")
            append("Start a new line each time the speaker changes and prefix it with the speaker's name if it is said, ")
            append("otherwise 'Speaker 1:', 'Speaker 2:' and so on. ")
            if (languageName != null && language != "auto") append("The main spoken language is $languageName. ")
            append(NigerianSpeech.transcriptionGuidance(options.nigerian, options.glossary))
            append("Do not summarise, translate or add commentary. Output only the transcript text.")
        }
        val model = settings.current().geminiModel
        val response = apiCall("Gemini") {
            api.generate(
                model = model,
                apiKey = key,
                body = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(
                                GeminiPart(text = instruction),
                                // .m4a = AAC audio in an MP4 container.
                                GeminiPart(inlineData = GeminiInlineData(mimeType = "audio/mp4", data = base64)),
                            ),
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(temperature = 0.0),
                ),
            )
        }
        val text = response.text().trim()
        check(text.isNotEmpty()) { "Gemini returned an empty transcript" }
        onProgress(1f, text)
        return text
    }
}
