package com.meetnotes.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meetnotes.app.MeetNotesApp
import com.meetnotes.app.R
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.data.repository.DocumentRepository
import com.meetnotes.app.domain.model.MeetingStatus
import com.meetnotes.app.domain.model.ProcessMode
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.repository.MeetingRepository
import com.meetnotes.app.domain.usecase.SummarizeMeetingUseCase
import com.meetnotes.app.domain.usecase.TranscribeMeetingUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Runs transcription and summarization for one meeting. Runs as a foreground job so long
 * recordings survive the app being backgrounded.
 */
@HiltWorker
class ProcessMeetingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transcribe: TranscribeMeetingUseCase,
    private val summarize: SummarizeMeetingUseCase,
    private val repo: MeetingRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Processing meeting…")

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_MEETING_ID, -1L)
        if (id < 0) return Result.failure()
        val mode = inputData.getString(KEY_MODE)?.let { runCatching { ProcessMode.valueOf(it) }.getOrNull() } ?: ProcessMode.ALL
        val tone = inputData.getString(KEY_TONE)?.let { runCatching { SummaryTone.valueOf(it) }.getOrNull() }

        // Best effort: may be refused when the app is in the background on Android 12+.
        runCatching { setForeground(foregroundInfo("Processing meeting…")) }

        return try {
            if (mode != ProcessMode.SUMMARIZE) {
                runCatching { setForeground(foregroundInfo("Transcribing meeting…")) }
                transcribe(id)
            }
            if (mode != ProcessMode.TRANSCRIBE) {
                runCatching { setForeground(foregroundInfo("Writing minutes…")) }
                summarize(id, tone)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            repo.setStatus(id, MeetingStatus.FAILED, null, e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }

    private fun foregroundInfo(text: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MeetNotesApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mokwa Meeting Note")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_MODE = "mode"
        const val KEY_TONE = "tone"
        const val TAG = "process_meeting"
        private const val NOTIFICATION_ID = 2001
    }
}

/** Reads an imported document and writes its key points. */
@HiltWorker
class ProcessDocumentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val documents: DocumentRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = documentForegroundInfo(applicationContext)

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_DOCUMENT_ID, -1L)
        if (id < 0) return Result.failure()
        runCatching { setForeground(documentForegroundInfo(applicationContext)) }
        documents.process(id)
        return Result.success()
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val TAG = "process_document"
        private const val NOTIFICATION_ID = 2002

        fun documentForegroundInfo(context: Context): ForegroundInfo {
            val notification = NotificationCompat.Builder(context, MeetNotesApp.CHANNEL_PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Mokwa Meeting Note")
                .setContentText("Summarising document…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .build()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }
    }
}

/** Daily job that removes recordings older than the "auto-delete" setting. */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MeetingRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val days = settings.current().autoDeleteDays
        if (days > 0) repo.deleteOlderThan(System.currentTimeMillis() - days * DAY_MS)
        return Result.success()
    }

    companion object {
        private const val DAY_MS = 86_400_000L

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "auto_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
