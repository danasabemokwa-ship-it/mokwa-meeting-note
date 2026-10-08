package com.meetnotes.app.ai.transcription

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.meetnotes.app.ai.NigerianSpeech
import com.meetnotes.app.domain.model.TranscriptionEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI bridge to whisper.cpp (https://github.com/ggerganov/whisper.cpp).
 *
 * The native library is NOT bundled by default (it must be compiled with the NDK). Follow
 * "On-device Whisper" in README.md: copy whisper.cpp's examples/whisper.android/lib JNI sources into
 * app/src/main/cpp, rename the JNI functions to Java_com_meetnotes_app_ai_transcription_WhisperLib_<name>,
 * and build a library named "whisper_android". Until then [isLoaded] is false and the app falls back
 * gracefully (Settings shows the engine as "not set up").
 */
object WhisperLib {
    val isLoaded: Boolean = runCatching { System.loadLibrary("whisper_android") }.isSuccess

    @JvmStatic external fun initContext(modelPath: String): Long
    @JvmStatic external fun freeContext(contextPtr: Long)
    /**
     * [language] is an ISO-639-1 code or "auto" → whisper_full_params.language.
     * [prompt] is an optional hint (names, Nigerian context) → whisper_full_params.initial_prompt ("" = none).
     */
    @JvmStatic external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String, prompt: String)
    @JvmStatic external fun getTextSegmentCount(contextPtr: Long): Int
    @JvmStatic external fun getTextSegment(contextPtr: Long, index: Int): String
}

/**
 * Fully offline transcription with a ggml Whisper model (e.g. ggml-base.bin ≈ 142 MB, or
 * ggml-small.bin ≈ 466 MB for better accuracy in Hausa and other non-English languages).
 * Import the model from Settings → "Import Whisper model".
 */
@Singleton
class LocalWhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: AudioDecoder,
) : TranscriptionEngine {

    override val type = TranscriptionEngineType.LOCAL_WHISPER

    private val modelDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun installedModel(): File? =
        modelDir.listFiles { f -> f.isFile && f.extension == "bin" }?.maxByOrNull { it.lastModified() }

    override val setupHint: String
        get() = when {
            !WhisperLib.isLoaded -> "The whisper.cpp native library isn't included in this build (see README)."
            installedModel() == null -> "Import a ggml Whisper model (.bin) in Settings."
            else -> ""
        }

    fun statusText(): String = when {
        !WhisperLib.isLoaded -> "Native library not bundled"
        else -> installedModel()?.let { "Model: ${it.name} (${it.length() / 1_000_000} MB)" } ?: "No model imported"
    }

    override suspend fun isAvailable(): Boolean = WhisperLib.isLoaded && installedModel() != null

    override suspend fun transcribe(audio: File, options: TranscriptionOptions, onProgress: ProgressCallback): String {
        check(isAvailable()) { setupHint }
        val model = installedModel()!!
        onProgress(0.05f, null)
        val samples = withContext(Dispatchers.IO) { decoder.decodeToMono16k(audio) }
        onProgress(0.15f, null)

        return withContext(Dispatchers.Default) {
            val ctx = WhisperLib.initContext(model.absolutePath)
            check(ctx != 0L) { "Could not load Whisper model ${model.name}" }
            try {
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                WhisperLib.fullTranscribe(
                    ctx, threads, samples,
                    NigerianSpeech.whisperLanguage(options.language),
                    NigerianSpeech.whisperPrompt(options.nigerian, options.glossary).orEmpty(),
                )
                val count = WhisperLib.getTextSegmentCount(ctx)
                val sb = StringBuilder()
                for (i in 0 until count) {
                    sb.appendLine(WhisperLib.getTextSegment(ctx, i).trim())
                    if (i % 10 == 0 || i == count - 1) {
                        onProgress(0.9f + 0.1f * (i + 1) / count, sb.toString().trim())
                    }
                }
                sb.toString().trim()
            } finally {
                WhisperLib.freeContext(ctx)
            }
        }
    }

    /** Copies a user-picked model file (Storage Access Framework URI) into private storage. */
    suspend fun importModel(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.let { if (it.endsWith(".bin")) it else "$it.bin" }
            ?: "ggml-model.bin"
        val target = File(modelDir, name)
        val tmp = File(modelDir, "$name.part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it, bufferSize = 1 shl 16) }
        } ?: error("Could not open the selected file")
        // Keep a single model to save space.
        modelDir.listFiles { f -> f.extension == "bin" }?.forEach { it.delete() }
        check(tmp.renameTo(target)) { "Could not save model" }
        target
    }

    fun deleteModel() {
        modelDir.listFiles()?.forEach { it.delete() }
    }
}
