package com.meetnotes.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.meetnotes.app.domain.model.AudioQuality
import java.io.File

/** Thin wrapper around MediaRecorder producing mono AAC in an .m4a container. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var paused = false

    fun start(file: File, quality: AudioQuality) {
        release()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(quality.sampleRate)
            r.setAudioEncodingBitRate(quality.bitRate)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            throw e
        }
        recorder = r
        paused = false
    }

    fun pause() {
        recorder?.pause()
        paused = true
    }

    fun resume() {
        recorder?.resume()
        paused = false
    }

    /** Peak amplitude (0..32767) since the last call. */
    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** @return true if the file was finalised correctly. */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            if (paused) runCatching { r.resume() }
            r.stop()
            true
        } catch (e: RuntimeException) {
            false // stop() throws if no audio data was captured
        } finally {
            r.release()
            paused = false
        }
    }

    fun release() {
        recorder?.release()
        recorder = null
        paused = false
    }
}
