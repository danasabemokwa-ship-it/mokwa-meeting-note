package com.meetnotes.app.ai.documents

import com.meetnotes.app.domain.model.DocumentSummary
import com.meetnotes.app.domain.model.normalized
import kotlinx.serialization.json.Json

/** Prompt contract for summarising an imported document. Keys match [DocumentSummary]. */
object DocumentPrompts {

    const val MAX_CHARS = 150_000

    fun system(glossary: List<String>): String = buildString {
        append(
            """
            You are a senior analyst who writes clear executive briefs. You read a document (report, memo, proposal,
            minutes, letter, policy or spreadsheet) and pull out what a busy manager needs to know.

            Return ONLY one JSON object — no markdown, no code fences, no other text — with EXACTLY these keys:
            {
              "title": string,
              "document_type": string,
              "overview": string,
              "key_points": [string],
              "key_figures": [string],
              "recommendations": [string],
              "action_items": [ { "owner": string, "task": string, "due_date": string, "priority": "High" | "Medium" | "Low" } ],
              "issues_risks": [string]
            }

            Rules:
            1. Use only information in the document. Never invent names, numbers, dates or conclusions.
            2. title: the document's own title if it has one, otherwise a short descriptive title (max 12 words).
            3. document_type: e.g. "Report", "Memo", "Proposal", "Minutes of meeting", "Budget", "Letter", "Spreadsheet".
            4. overview: 2–4 sentences: what the document is, its purpose and its main conclusion.
            5. key_points: the 5–10 most important points, findings or arguments, most important first, one per string, each a complete sentence.
            6. key_figures: important numbers with what they mean (amounts, percentages, targets vs achieved, dates). For spreadsheets,
               copy figures from the PRE-COMPUTED STATISTICS exactly — do not do your own arithmetic. Use [] if none.
            7. recommendations: recommendations, proposals or requests made in the document. Use [] if none.
            8. action_items: tasks or follow-ups the document assigns or implies, starting with a verb. owner = person or office named,
               or "TBD". due_date as stated, or "TBD". priority High when urgent or near deadline, Low when optional, else Medium.
            9. issues_risks: problems, gaps, risks or challenges raised. Use [] if none.
            10. Write in clear, formal English even if the document mixes languages. Keep Nigerian names, places and acronyms exactly as written.
            """.trimIndent()
        )
        if (glossary.isNotEmpty()) {
            append("\n\nSpell these names and terms exactly: ").append(glossary.joinToString(", "))
        }
    }

    fun user(fileName: String, text: String, statistics: String): String = buildString {
        appendLine("File name: $fileName")
        if (statistics.isNotBlank()) {
            appendLine()
            appendLine("PRE-COMPUTED STATISTICS (exact, computed from every row):")
            appendLine(statistics)
        }
        appendLine()
        appendLine("DOCUMENT TEXT:")
        appendLine("\"\"\"")
        appendLine(if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n[…document truncated…]" else text)
        appendLine("\"\"\"")
    }

    /** Used when the PDF has no text layer and the whole file is sent to Gemini. */
    fun userForAttachedFile(fileName: String): String =
        "File name: $fileName\nThe document is attached (it may be scanned). Read every page and return the JSON."
}

object DocumentSummaryParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(raw: String): DocumentSummary {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "The AI response did not contain a document summary." }
        return json.decodeFromString(DocumentSummary.serializer(), raw.substring(start, end + 1)).normalized()
    }
}
