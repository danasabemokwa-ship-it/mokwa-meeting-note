package com.meetnotes.app.export

import com.meetnotes.app.domain.model.ActionItem
import com.meetnotes.app.domain.model.Meeting
import com.meetnotes.app.util.Formatters

/** Converts a meeting into Markdown, plain text or PDF blocks. Action items come from the
 *  editable checklist (not the original AI output) so user edits are always exported. */
object MinutesFormatter {

    fun markdown(m: Meeting, actions: List<ActionItem>, includeTranscript: Boolean = true): String = buildString {
        val s = m.summary
        appendLine("# ${s?.title?.ifBlank { null } ?: m.title}")
        appendLine()
        appendLine("**Date & Time:** ${s?.dateTime?.ifBlank { null } ?: Formatters.dateTime(m.createdAt)}  ")
        appendLine("**Duration:** ${Formatters.duration(m.durationMs)}  ")
        appendLine("**Participants:** ${s?.participants?.joinToString(", ")?.ifBlank { null } ?: "Not recorded"}")
        if (m.tags.isNotEmpty()) appendLine("**Tags:** ${m.tags.joinToString(", ")}")
        appendLine()
        section("Key Discussion Points", s?.keyPoints.orEmpty())
        section("Decisions Made", s?.decisions.orEmpty())
        appendLine("## Action Items")
        if (actions.isEmpty()) appendLine("_None recorded._") else {
            appendLine("| # | Task | Owner | Due Date | Priority | Status |")
            appendLine("|---|------|-------|----------|----------|--------|")
            actions.forEachIndexed { i, a ->
                appendLine("| ${i + 1} | ${a.task.cell()} | ${a.owner.cell()} | ${a.dueDate.cell()} | ${a.priority.label} | ${if (a.done) "✅ Done" else "Open"} |")
            }
        }
        appendLine()
        section("Next Steps / Follow-up", s?.nextSteps.orEmpty())
        if (m.notes.isNotBlank()) {
            appendLine("## Notes")
            appendLine(m.notes.trim())
            appendLine()
        }
        if (includeTranscript && !m.transcript.isNullOrBlank()) {
            appendLine("---")
            appendLine("## Appendix: Transcript")
            appendLine()
            appendLine(m.transcript.trim())
            appendLine()
        }
        appendLine("---")
        append("_Generated with Mokwa Meeting Note")
        if (!s?.generatedBy.isNullOrBlank()) append(" · ${s?.generatedBy}")
        appendLine("_")
    }

    fun plainText(m: Meeting, actions: List<ActionItem>, includeTranscript: Boolean = true): String = buildString {
        val s = m.summary
        val title = s?.title?.ifBlank { null } ?: m.title
        appendLine("MINUTES OF MEETING")
        appendLine(title.uppercase())
        appendLine("=".repeat(minOf(title.length, 60)))
        appendLine("Date & Time : ${s?.dateTime?.ifBlank { null } ?: Formatters.dateTime(m.createdAt)}")
        appendLine("Duration    : ${Formatters.duration(m.durationMs)}")
        appendLine("Participants: ${s?.participants?.joinToString(", ")?.ifBlank { null } ?: "Not recorded"}")
        appendLine()
        plainSection("KEY DISCUSSION POINTS", s?.keyPoints.orEmpty())
        plainSection("DECISIONS MADE", s?.decisions.orEmpty())
        appendLine("ACTION ITEMS")
        if (actions.isEmpty()) appendLine("  None recorded.") else actions.forEachIndexed { i, a ->
            appendLine("  ${i + 1}. [${if (a.done) "x" else " "}] ${a.task}")
            appendLine("     Owner: ${a.owner} | Due: ${a.dueDate} | Priority: ${a.priority.label}")
        }
        appendLine()
        plainSection("NEXT STEPS / FOLLOW-UP", s?.nextSteps.orEmpty())
        if (m.notes.isNotBlank()) {
            appendLine("NOTES")
            appendLine(m.notes.trim())
            appendLine()
        }
        if (includeTranscript && !m.transcript.isNullOrBlank()) {
            appendLine("TRANSCRIPT")
            appendLine(m.transcript.trim())
        }
    }

    /** Short, chat-friendly list for "Copy action items". */
    fun actionItemsText(m: Meeting, actions: List<ActionItem>): String = buildString {
        appendLine("Action items – ${m.summary?.title?.ifBlank { null } ?: m.title} (${Formatters.date(m.createdAt)})")
        if (actions.isEmpty()) appendLine("None recorded.")
        actions.forEachIndexed { i, a ->
            appendLine("${i + 1}. ${if (a.done) "☑" else "☐"} ${a.task} — Owner: ${a.owner} — Due: ${a.dueDate} — ${a.priority.label} priority")
        }
    }.trim()

    fun documentBlocks(m: Meeting, actions: List<ActionItem>, includeTranscript: Boolean = true): List<DocBlock> {
        val s = m.summary
        val blocks = mutableListOf<DocBlock>()
        blocks += DocBlock.Title(s?.title?.ifBlank { null } ?: m.title)
        blocks += DocBlock.Meta("Date & Time", s?.dateTime?.ifBlank { null } ?: Formatters.dateTime(m.createdAt))
        blocks += DocBlock.Meta("Duration", Formatters.duration(m.durationMs))
        blocks += DocBlock.Meta("Participants", s?.participants?.joinToString(", ")?.ifBlank { null } ?: "Not recorded")
        if (m.tags.isNotEmpty()) blocks += DocBlock.Meta("Tags", m.tags.joinToString(", "))

        fun list(heading: String, items: List<String>) {
            blocks += DocBlock.Heading(heading)
            if (items.isEmpty()) blocks += DocBlock.Paragraph("None recorded.")
            else items.forEach { blocks += DocBlock.Bullet(it) }
        }
        list("Key Discussion Points", s?.keyPoints.orEmpty())
        list("Decisions Made", s?.decisions.orEmpty())
        blocks += DocBlock.Heading("Action Items")
        if (actions.isEmpty()) blocks += DocBlock.Paragraph("None recorded.")
        actions.forEach { blocks += DocBlock.ActionRow(it.task, it.owner, it.dueDate, it.done, it.priority.label) }
        list("Next Steps / Follow-up", s?.nextSteps.orEmpty())
        if (m.notes.isNotBlank()) {
            blocks += DocBlock.Heading("Notes")
            m.notes.trim().lines().filter { it.isNotBlank() }.forEach { blocks += DocBlock.Paragraph(it) }
        }
        if (includeTranscript && !m.transcript.isNullOrBlank()) {
            blocks += DocBlock.PageBreak
            blocks += DocBlock.Heading("Appendix: Transcript")
            m.transcript.lines().filter { it.isNotBlank() }
                .flatMap { it.chunked(900) }
                .forEach { blocks += DocBlock.Small(it) }
        }
        val by = s?.generatedBy?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        blocks += DocBlock.Small("Prepared with Mokwa Meeting Note$by")
        return blocks
    }

    /** Professional email body: short cover note, then the minutes in readable plain text. */
    fun emailBody(m: Meeting, actions: List<ActionItem>, userName: String, organisation: String, attachedLabel: String?): String = buildString {
        val s = m.summary
        val title = s?.title?.ifBlank { null } ?: m.title
        appendLine("Dear all,")
        appendLine()
        append("Please find below the minutes of the meeting \"$title\" held on ${Formatters.dateTime(m.createdAt)}")
        if (attachedLabel != null) append(". The full minutes are also attached as a $attachedLabel")
        appendLine(".")
        appendLine()
        appendLine("SUMMARY")
        val participants = s?.participants.orEmpty()
        if (participants.isNotEmpty()) appendLine("Participants: ${participants.joinToString(", ")}")
        appendLine("Duration: ${Formatters.duration(m.durationMs)}")
        appendLine()
        emailList("KEY DISCUSSION POINTS", s?.keyPoints.orEmpty())
        emailList("DECISIONS", s?.decisions.orEmpty())
        appendLine("ACTION POINTS")
        if (actions.isEmpty()) appendLine("None recorded.")
        actions.forEachIndexed { i, a ->
            appendLine("${i + 1}. ${a.task}${if (a.done) " (done)" else ""}")
            appendLine("   Owner: ${a.owner}  |  Due: ${a.dueDate}  |  Priority: ${a.priority.label}")
        }
        appendLine()
        emailList("NEXT STEPS", s?.nextSteps.orEmpty())
        appendLine("Kindly review and send any corrections.")
        appendLine()
        appendLine("Best regards,")
        if (userName.isNotBlank()) appendLine(userName)
        if (organisation.isNotBlank()) appendLine(organisation)
        appendLine()
        append("— Prepared with Mokwa Meeting Note")
    }

    /** Reminder to everyone who owns an open action point, grouped by owner. */
    fun actionOwnersEmailBody(title: String, createdAt: Long, open: List<ActionItem>, userName: String): String = buildString {
        appendLine("Dear colleagues,")
        appendLine()
        appendLine("Following the meeting \"$title\" held on ${Formatters.date(createdAt)}, below are the open action points and their owners:")
        appendLine()
        if (open.isEmpty()) appendLine("All action points have been completed. Thank you!")
        open.groupBy { it.owner.ifBlank { "TBD" } }.forEach { (owner, items) ->
            appendLine(owner.uppercase())
            items.forEach { a -> appendLine("  • ${a.task} — due ${a.dueDate} (${a.priority.label} priority)") }
            appendLine()
        }
        appendLine("Kindly share a status update on your items before the due dates.")
        appendLine()
        appendLine("Thank you,")
        if (userName.isNotBlank()) appendLine(userName)
    }

    private fun StringBuilder.emailList(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine(title)
        items.forEach { appendLine("• $it") }
        appendLine()
    }

    private fun StringBuilder.section(title: String, items: List<String>) {
        appendLine("## $title")
        if (items.isEmpty()) appendLine("_None recorded._") else items.forEach { appendLine("- $it") }
        appendLine()
    }

    private fun StringBuilder.plainSection(title: String, items: List<String>) {
        appendLine(title)
        if (items.isEmpty()) appendLine("  None recorded.") else items.forEach { appendLine("  • $it") }
        appendLine()
    }

    private fun String.cell() = replace("|", "/").replace("\n", " ")
}
