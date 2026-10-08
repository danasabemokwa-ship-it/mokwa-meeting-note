package com.meetnotes.app.ai.transcription

import com.meetnotes.app.domain.model.TranscriptionEngineType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the engine should know besides the audio itself. */
data class TranscriptionOptions(
    /** Language setting: ISO-639-1 code, "en-NG", "pcm" or "auto". */
    val language: String,
    /** Add Nigerian accent / Pidgin / code-switching context. */
    val nigerian: Boolean = false,
    /** Names, places and acronyms to spell correctly. */
    val glossary: List<String> = emptyList(),
)

/** Reports progress 0..1 and, when the engine supports it, the partial transcript so far. */
typealias ProgressCallback = suspend (progress: Float, partialText: String?) -> Unit

/**
 * Abstraction over every speech-to-text backend. Add a new engine by implementing this interface,
 * adding a value to [TranscriptionEngineType] and registering it in [TranscriptionEngineFactory].
 */
interface TranscriptionEngine {
    val type: TranscriptionEngineType

    /** True when the engine is configured (model present / API key saved). */
    suspend fun isAvailable(): Boolean

    /** One-line instruction shown when [isAvailable] is false. */
    val setupHint: String

    /** @return the full transcript as plain text. */
    suspend fun transcribe(audio: File, options: TranscriptionOptions, onProgress: ProgressCallback = { _, _ -> }): String
}

@Singleton
class TranscriptionEngineFactory @Inject constructor(
    private val demo: DemoTranscriptionEngine,
    private val local: LocalWhisperEngine,
    private val openAi: OpenAiWhisperEngine,
    private val gemini: GeminiTranscriptionEngine,
) {
    fun get(type: TranscriptionEngineType): TranscriptionEngine = when (type) {
        TranscriptionEngineType.DEMO -> demo
        TranscriptionEngineType.LOCAL_WHISPER -> local
        TranscriptionEngineType.OPENAI_WHISPER -> openAi
        TranscriptionEngineType.GEMINI -> gemini
    }

    val all: List<TranscriptionEngine> get() = TranscriptionEngineType.entries.map(::get)
}
