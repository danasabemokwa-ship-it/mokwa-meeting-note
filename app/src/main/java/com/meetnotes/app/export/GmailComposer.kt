package com.meetnotes.app.export

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.meetnotes.app.data.prefs.SettingsRepository
import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.ActionWithMeeting
import com.meetnotes.app.domain.model.DocumentItem
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.util.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends minutes and action reminders through the user's own Gmail app: the email opens fully
 * written (recipients, subject, body, PDF/Word attached) and the user just taps Send. Nothing is
 * sent without the user seeing it, and no Google account password or API setup is needed.
 * Falls back to any installed email app when Gmail isn't available.
 */
@Singleton
class GmailComposer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: ExportManager,
    private val settings: SettingsRepository,
) {
    fun isGmailInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(GMAIL_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * @param attachment null = no attachment (minutes in the email body only).
     */
    suspend fun minutesEmail(
        meeting: Meeting,
        actions: List<ActionItem>,
        to: List<String>,
        cc: List<String>,
        attachment: ExportFormat?,
        includeTranscript: Boolean,
    ): Intent {
        val profile = settings.current()
        val title = meeting.summary?.title?.ifBlank { null } ?: meeting.title
        val file = attachment?.let { exporter.writeFile(meeting, actions, it, includeTranscript) }
        val attachedLabel = when (attachment) {
            null -> null
            else -> attachment.label
        }
        val body = MinutesFormatter.emailBody(meeting, actions, profile.userName, profile.organisation, attachedLabel)
        val subject = "Minutes: $title – ${Formatters.date(meeting.createdAt)}"
        return compose(to, cc, subject, body, file?.let { exporter.uriFor(it.file) }, attachment?.mime)
    }

    /** Key points of an imported document, with the brief attached. */
    suspend fun documentEmail(d: DocumentItem, to: List<String>, cc: List<String>, attachment: ExportFormat?): Intent {
        val profile = settings.current()
        val file = attachment?.let { exporter.writeDocumentSummary(d, it) }
        val body = DocumentFormatter.emailBody(d, profile.userName, profile.organisation, attachment?.label)
        return compose(to, cc, "Key points: ${DocumentFormatter.title(d)}", body, file?.let { exporter.uriFor(it.file) }, attachment?.mime)
    }

    /** Recipients saved in Settings → Profile ("Default recipients"). */
    suspend fun defaultRecipients(): List<String> = parseEmails(settings.current().defaultRecipients)

    /** One reminder email to every owner of an open action item, grouped by person. */
    suspend fun actionOwnersEmail(meeting: Meeting, actions: List<ActionItem>): Intent {
        val profile = settings.current()
        val title = meeting.summary?.title?.ifBlank { null } ?: meeting.title
        val open = actions.filter { !it.done }
        val to = open.map { it.ownerEmail.trim() }.filter { it.contains('@') }.distinct()
        val body = MinutesFormatter.actionOwnersEmailBody(title, meeting.createdAt, open, profile.userName)
        return compose(to, emptyList(), "Action points: $title", body, null, null)
    }

    /** Status-update request covering open action points from several meetings. */
    suspend fun trackerEmail(open: List<ActionWithMeeting>): Intent {
        val profile = settings.current()
        val to = open.map { it.item.ownerEmail.trim() }.filter { it.contains('@') }.distinct()
        val body = buildString {
            appendLine("Dear colleagues,")
            appendLine()
            appendLine("Below are the open action points from our recent meetings. Kindly share a status update on your items.")
            appendLine()
            open.groupBy { it.item.owner.ifBlank { "TBD" } }.forEach { (owner, items) ->
                appendLine(owner.uppercase())
                items.forEach { a ->
                    val due = a.item.dueAt?.let { com.meetnotes.app.util.DueDates.label(it) } ?: a.item.dueDate
                    val late = if (a.item.isOverdue()) " — OVERDUE" else ""
                    appendLine("  • ${a.item.task} (due $due, ${a.item.priority.label} priority$late)")
                    appendLine("    From: ${a.meetingTitle}, ${Formatters.date(a.meetingDate)}")
                }
                appendLine()
            }
            appendLine("Thank you,")
            if (profile.userName.isNotBlank()) appendLine(profile.userName)
            if (profile.organisation.isNotBlank()) appendLine(profile.organisation)
        }
        return compose(to, emptyList(), "Open action points – status update", body, null, null)
    }

    /** Reminder for a single action item. */
    suspend fun singleActionEmail(meetingTitle: String, meetingDate: Long, item: ActionItem): Intent {
        val profile = settings.current()
        val body = buildString {
            appendLine("Dear ${item.owner.substringBefore(' ').ifBlank { "colleague" }},")
            appendLine()
            appendLine("This is a reminder of the action point assigned to you at the meeting \"$meetingTitle\" (${Formatters.date(meetingDate)}):")
            appendLine()
            appendLine("• ${item.task}")
            appendLine("  Due: ${item.dueDate}   |   Priority: ${item.priority.label}")
            appendLine()
            appendLine("Kindly share a status update.")
            appendLine()
            appendLine("Thank you,")
            if (profile.userName.isNotBlank()) appendLine(profile.userName)
        }
        val to = listOf(item.ownerEmail).filter { it.contains('@') }
        return compose(to, emptyList(), "Action point: ${item.task.take(60)}", body, null, null)
    }

    private fun compose(
        to: List<String>,
        cc: List<String>,
        subject: String,
        body: String,
        attachmentUri: android.net.Uri?,
        attachmentMime: String?,
    ): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = attachmentMime ?: "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            if (cc.isNotEmpty()) putExtra(Intent.EXTRA_CC, cc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (attachmentUri != null) {
                putExtra(Intent.EXTRA_STREAM, attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return if (isGmailInstalled()) {
            send.setPackage(GMAIL_PACKAGE)
        } else {
            Intent.createChooser(send, "Send email with…")
        }
    }

    companion object {
        const val GMAIL_PACKAGE = "com.google.android.gm"

        fun parseEmails(raw: String): List<String> =
            raw.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.contains('@') && it.contains('.') }.distinct()
    }
}
