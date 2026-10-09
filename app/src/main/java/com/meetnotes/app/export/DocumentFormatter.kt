package com.meetnotes.app.export

import com.meetnotes.app.domain.model.DocumentItem
import com.meetnotes.app.domain.model.DocumentSummary
import com.meetnotes.app.domain.model.Priority
import com.meetnotes.app.util.Formatters

/** Renders a document's key-point summary as a brief (Word / PDF blocks, or text for email). */
object DocumentFormatter {

    fun title(d: DocumentItem): String = d.summary?.title?.ifBlank { null } ?: d.name

    fun blocks(d: DocumentItem): List<DocBlock> {
        val s = d.summary ?: DocumentSummary()
        val out = mutableListOf<DocBlock>()
        out += DocBlock.Title("Brief: ${title(d)}")
        out += DocBlock.Meta("Source file", "${d.name} (${d.kind.label})")
        if (s.documentType.isNotBlank()) out += DocBlock.Meta("Document type", s.documentType)
        if (d.meta.isNotBlank()) out += DocBlock.Meta("Size", d.meta)
        out += DocBlock.Meta("Summarised", Formatters.dateTime(d.createdAt))
        if (s.overview.isNotBlank()) {
            out += DocBlock.Heading("Overview")
            out += DocBlock.Paragraph(s.overview)
        }
        fun list(h: String, items: List<String>) {
            if (items.isEmpty()) return
            out += DocBlock.Heading(h)
            items.forEach { out += DocBlock.Bullet(it) }
        }
        list("Key Points", s.keyPoints)
        list("Key Figures", s.keyFigures)
        list("Recommendations", s.recommendations)
        list("Issues & Risks", s.issues)
        if (s.actionItems.isNotEmpty()) {
            out += DocBlock.Heading("Action Points")
            s.actionItems.forEach { out += DocBlock.ActionRow(it.task, it.owner, it.dueDate, false, Priority.parse(it.priority).label) }
        }
        val by = s.generatedBy.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        out += DocBlock.Small("Prepared with Mokwa Meeting Note$by")
        return out
    }

    fun plainText(d: DocumentItem): String = buildString {
        val s = d.summary ?: DocumentSummary()
        appendLine("BRIEF: ${title(d).uppercase()}")
        appendLine("Source: ${d.name} (${d.kind.label}${if (d.meta.isNotBlank()) ", ${d.meta}" else ""})")
        appendLine()
        if (s.overview.isNotBlank()) { appendLine("OVERVIEW"); appendLine(s.overview); appendLine() }
        fun list(h: String, items: List<String>) {
            if (items.isEmpty()) return
            appendLine(h)
            items.forEach { appendLine("• $it") }
            appendLine()
        }
        list("KEY POINTS", s.keyPoints)
        list("KEY FIGURES", s.keyFigures)
        list("RECOMMENDATIONS", s.recommendations)
        list("ISSUES & RISKS", s.issues)
        if (s.actionItems.isNotEmpty()) {
            appendLine("ACTION POINTS")
            s.actionItems.forEachIndexed { i, a ->
                appendLine("${i + 1}. ${a.task}")
                appendLine("   Owner: ${a.owner}  |  Due: ${a.dueDate}  |  Priority: ${Priority.parse(a.priority).label}")
            }
            appendLine()
        }
    }.trim()

    fun markdown(d: DocumentItem): String = buildString {
        val s = d.summary ?: DocumentSummary()
        appendLine("# Brief: ${title(d)}")
        appendLine()
        appendLine("**Source:** ${d.name} (${d.kind.label})  ")
        if (s.documentType.isNotBlank()) appendLine("**Type:** ${s.documentType}")
        appendLine()
        if (s.overview.isNotBlank()) { appendLine("## Overview"); appendLine(s.overview); appendLine() }
        fun list(h: String, items: List<String>) {
            if (items.isEmpty()) return
            appendLine("## $h")
            items.forEach { appendLine("- $it") }
            appendLine()
        }
        list("Key Points", s.keyPoints)
        list("Key Figures", s.keyFigures)
        list("Recommendations", s.recommendations)
        list("Issues & Risks", s.issues)
        if (s.actionItems.isNotEmpty()) {
            appendLine("## Action Points")
            appendLine("| # | Task | Owner | Due | Priority |")
            appendLine("|---|------|-------|-----|----------|")
            s.actionItems.forEachIndexed { i, a ->
                appendLine("| ${i + 1} | ${a.task.replace("|", "/")} | ${a.owner} | ${a.dueDate} | ${Priority.parse(a.priority).label} |")
            }
        }
    }

    fun emailBody(d: DocumentItem, userName: String, organisation: String, attachedLabel: String?): String = buildString {
        appendLine("Dear all,")
        appendLine()
        append("Below is a summary of the key points of \"${title(d)}\"")
        if (attachedLabel != null) append(". The brief is also attached as a $attachedLabel")
        appendLine(".")
        appendLine()
        appendLine(plainText(d))
        appendLine()
        appendLine("Best regards,")
        if (userName.isNotBlank()) appendLine(userName)
        if (organisation.isNotBlank()) appendLine(organisation)
        appendLine()
        append("— Prepared with Mokwa Meeting Note")
    }
}
