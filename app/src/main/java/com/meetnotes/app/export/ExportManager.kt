package com.meetnotes.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.util.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    PDF("PDF document", "pdf", "application/pdf"),
    WORD("Word document", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    TEXT("Plain text", "txt", "text/plain"),
}

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdf: PdfExporter,
    private val docx: DocxExporter,
) {
    private val authority get() = "${context.packageName}.fileprovider"

    /** A finished export ready to share or save. */
    data class ExportedFile(val file: File, val format: ExportFormat, val title: String, val inlineText: String?)

    /** Writes the minutes in [format] to the app cache. */
    suspend fun writeFile(meeting: Meeting, actions: List<ActionItem>, format: ExportFormat, includeTranscript: Boolean): ExportedFile =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val title = meeting.summary?.title?.ifBlank { null } ?: meeting.title
            val file = File(dir, fileName(meeting, format))
            val blocks by lazy { MinutesFormatter.documentBlocks(meeting, actions, includeTranscript) }
            val textBody: String? = when (format) {
                ExportFormat.PDF -> { pdf.write(file, blocks); null }
                ExportFormat.WORD -> { docx.write(file, blocks, title); null }
                ExportFormat.MARKDOWN -> MinutesFormatter.markdown(meeting, actions, includeTranscript).also { file.writeText(it) }
                ExportFormat.TEXT -> MinutesFormatter.plainText(meeting, actions, includeTranscript).also { file.writeText(it) }
            }
            ExportedFile(file, format, title, textBody)
        }

    /** Writes the minutes file and returns a share-sheet intent for it. */
    suspend fun export(meeting: Meeting, actions: List<ActionItem>, format: ExportFormat, includeTranscript: Boolean): Intent {
        val e = writeFile(meeting, actions, format, includeTranscript)
        // Many chat apps (e.g. WhatsApp) ignore text/markdown, so text formats are shared as text/plain
        // with the content inline as well as attached.
        val mime = if (format == ExportFormat.PDF || format == ExportFormat.WORD) format.mime else "text/plain"
        return shareFile(e.file, mime, "Minutes – ${e.title}", e.inlineText?.take(60_000))
    }

    /** Suggested file name, e.g. "Weekly Programme Review – 8 Oct 2026.docx". */
    fun fileName(meeting: Meeting, format: ExportFormat): String {
        val title = meeting.summary?.title?.ifBlank { null } ?: meeting.title
        val base = if (Formatters.isAutoTitle(title)) title else "$title - ${Formatters.date(meeting.createdAt)}"
        return "${Formatters.safeFileName(base)}.${format.extension}"
    }

    /** Copies an exported file to a location the user picked in the system "Save as" dialog. */
    suspend fun saveTo(file: File, destination: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("Could not open the chosen location")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }

    fun shareAudio(meeting: Meeting): Intent? {
        val file = File(meeting.audioPath).takeIf { it.exists() } ?: return null
        return shareFile(file, "audio/mp4", meeting.title, null)
    }

    fun shareText(subject: String, text: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text),
            "Share",
        )

    private fun shareFile(file: File, mime: String, subject: String, inlineText: String?): Intent {
        val uri = FileProvider.getUriForFile(context, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (inlineText != null) putExtra(Intent.EXTRA_TEXT, inlineText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share $subject")
    }

    /**
     * Opens the calendar app's "new event" screen pre-filled with the action item. No calendar
     * permission is needed because the user confirms the event in their calendar app.
     */
    fun calendarIntent(meeting: Meeting, item: ActionItem): Intent {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, item.task)
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Owner: ${item.owner}\nDue: ${item.dueDate}\nFrom meeting: ${meeting.title} (${Formatters.date(meeting.createdAt)})",
            )
        parseDueDate(item.dueDate, meeting.createdAt)?.let { start ->
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 86_400_000L)
        }
        return intent
    }

    /**
     * Understands ISO / numeric dates, "15 October", weekday names, "today", "tomorrow" and Nigerian
     * phrasing such as "next tomorrow", "latest by Friday", "close of business Monday", "weekend".
     */
    private fun parseDueDate(raw: String, reference: Long): Long? {
        var text = raw.trim().lowercase(Locale.ENGLISH).trimEnd('.')
            .removePrefix("latest by ").removePrefix("latest ").removePrefix("by ")
        val cob = Regex("^(close of business|cob)(\\s+on)?\\s*")
        val wasCob = cob.containsMatchIn(text)
        text = text.replace(cob, "")
        if (text.isBlank()) return if (wasCob) startOfDay(Calendar.getInstance().apply { timeInMillis = reference }) else null
        if (text == "tbd") return null
        val cal = Calendar.getInstance().apply { timeInMillis = reference }
        when (text) {
            "next tomorrow" -> return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, 2) })
            "weekend", "this weekend" -> text = "saturday"
            "end of the week", "end of week", "end of this week" -> text = "friday"
            "end of the month", "end of month", "end of this month" ->
                return startOfDay(cal.apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) })
        }
        text = text.removePrefix("next ").removePrefix("this ")
        when (text) {
            "today" -> return startOfDay(cal)
            "tomorrow" -> return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, 1) })
        }
        val weekdays = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val wd = weekdays.indexOf(text)
        if (wd >= 0) {
            val target = wd + 1 // Calendar.SUNDAY == 1
            var diff = target - cal.get(Calendar.DAY_OF_WEEK)
            if (diff <= 0) diff += 7
            return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, diff) })
        }
        val patterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "d MMMM yyyy", "d MMM yyyy", "MMMM d, yyyy", "MMMM d yyyy", "d MMMM", "d MMM", "MMMM d")
        val cleaned = text.replace(Regex("(\\d)(st|nd|rd|th)"), "$1").replace(" of ", " ")
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.ENGLISH).apply { isLenient = false }.parse(cleaned)
            }.getOrNull() ?: continue
            val result = Calendar.getInstance().apply { time = parsed }
            if (!p.contains("y")) {
                result.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                if (result.timeInMillis < reference - 86_400_000L) result.add(Calendar.YEAR, 1)
            }
            return startOfDay(result)
        }
        return null
    }

    private fun startOfDay(c: Calendar): Long = c.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
