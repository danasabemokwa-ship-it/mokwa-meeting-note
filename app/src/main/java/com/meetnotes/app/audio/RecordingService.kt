package com.meetnotes.app.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.meetnotes.app.MainActivity
import com.meetnotes.app.MeetNotesApp
import com.meetnotes.app.R
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.domain.repository.MeetingRepository
import com.meetnotes.app.domain.usecase.ScheduleProcessingUseCase
import com.meetnotes.app.util.Formatters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Foreground service (type "microphone") that owns the MediaRecorder, so recording continues when
 * the screen is off or the user switches apps. Controlled with the ACTION_* intents below.
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var holder: RecordingStateHolder
    @Inject lateinit var repo: MeetingRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduleProcessing: ScheduleProcessingUseCase

    private val recorder by lazy { AudioRecorder(this) }
    private var file: File? = null
    private var startedAtWallClock = 0L
    private var accumulatedMs = 0L
    private var segmentStart = 0L
    private var ticker: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
            ACTION_CANCEL -> cancel()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (holder.state.value.isActive) return
        // Must enter the foreground immediately after startForegroundService().
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(paused = false),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            holder.update { RecordingState(error = "Microphone permission is required to record.") }
            stopSelf()
            return
        }

        lifecycleScope.launch {
            val quality = settings.current().audioQuality
            val target = newRecordingFile()
            try {
                recorder.start(target, quality)
            } catch (e: Exception) {
                target.delete()
                holder.update { RecordingState(error = "Could not start the microphone: ${e.message}") }
                shutdown()
                return@launch
            }
            file = target
            startedAtWallClock = System.currentTimeMillis()
            accumulatedMs = 0
            segmentStart = SystemClock.elapsedRealtime()
            holder.update { RecordingState(status = RecStatus.RECORDING, fileName = target.nameWithoutExtension) }
            notifyState(paused = false)
            startTicker()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = lifecycleScope.launch {
            while (isActive) {
                if (holder.state.value.status == RecStatus.RECORDING) {
                    val elapsed = accumulatedMs + (SystemClock.elapsedRealtime() - segmentStart)
                    // sqrt makes quiet speech visible without clipping loud speech.
                    val level = sqrt((recorder.maxAmplitude() / 32767f).coerceIn(0f, 1f))
                    holder.update { it.copy(elapsedMs = elapsed, levels = (it.levels + level).takeLast(MAX_LEVELS)) }
                }
                delay(80)
            }
        }
    }

    private fun pause() {
        if (holder.state.value.status != RecStatus.RECORDING) return
        runCatching { recorder.pause() }.onFailure { return }
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        holder.update { it.copy(status = RecStatus.PAUSED, elapsedMs = accumulatedMs) }
        notifyState(paused = true)
    }

    private fun resume() {
        if (holder.state.value.status != RecStatus.PAUSED) return
        runCatching { recorder.resume() }.onFailure { return }
        segmentStart = SystemClock.elapsedRealtime()
        holder.update { it.copy(status = RecStatus.RECORDING) }
        notifyState(paused = false)
    }

    private fun stop() {
        val target = file ?: run { shutdown(); return }
        val state = holder.state.value
        val elapsed = if (state.status == RecStatus.RECORDING) {
            accumulatedMs + (SystemClock.elapsedRealtime() - segmentStart)
        } else accumulatedMs
        ticker?.cancel()
        val ok = recorder.stop()
        file = null

        lifecycleScope.launch {
            if (ok && target.exists() && target.length() > 0) {
                val id = repo.createMeeting(
                    title = target.nameWithoutExtension,
                    audioPath = target.absolutePath,
                    durationMs = elapsed,
                    createdAt = startedAtWallClock,
                )
                if (settings.current().autoProcess) scheduleProcessing(id)
                holder.update { RecordingState() }
                holder.emitFinished(id)
            } else {
                target.delete()
                holder.update { RecordingState(error = "The recording was too short or could not be saved.") }
            }
            shutdown()
        }
    }

    private fun cancel() {
        ticker?.cancel()
        recorder.stop()
        file?.delete()
        file = null
        holder.update { RecordingState() }
        shutdown()
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // If the system kills the service mid-recording, keep what was captured.
        if (file != null) {
            recorder.stop()
            holder.update { RecordingState(error = "Recording was interrupted.") }
        }
        recorder.release()
        super.onDestroy()
    }

    private fun newRecordingFile(): File {
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val base = "Meeting_" + Formatters.fileStamp(System.currentTimeMillis())
        var candidate = File(dir, "$base.m4a")
        var n = 2
        while (candidate.exists()) candidate = File(dir, "${base}_${n++}.m4a")
        return candidate
    }

    // ------------------------------------------------------------------ notification

    private fun notifyState(paused: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(paused))
    }

    private fun buildNotification(paused: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_RECORDER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, 1)
        val stop = servicePendingIntent(ACTION_STOP, 2)
        val state = holder.state.value

        return NotificationCompat.Builder(this, MeetNotesApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (paused) "Recording paused" else "Recording meeting")
            .setContentText(state.fileName ?: "Mokwa Meeting Note")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (paused) {
                    setShowWhen(false)
                } else {
                    setUsesChronometer(true)
                    setWhen(System.currentTimeMillis() - state.elapsedMs)
                }
            }
            .addAction(0, if (paused) "Resume" else "Pause", toggle)
            .addAction(0, "Stop & save", stop)
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ACTION_START = "com.meetnotes.app.action.START"
        const val ACTION_PAUSE = "com.meetnotes.app.action.PAUSE"
        const val ACTION_RESUME = "com.meetnotes.app.action.RESUME"
        const val ACTION_STOP = "com.meetnotes.app.action.STOP"
        const val ACTION_CANCEL = "com.meetnotes.app.action.CANCEL"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_LEVELS = 90

        fun send(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }
}
