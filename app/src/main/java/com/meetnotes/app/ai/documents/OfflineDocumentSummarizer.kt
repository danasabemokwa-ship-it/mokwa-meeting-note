package com.meetnotes.app.ai.documents

import com.meetnotes.app.domain.model.DocKind
import com.meetnotes.app.domain.model.DocumentSummary
import com.meetnotes.app.domain.model.SummaryActionItem
import com.meetnotes.app.domain.model.normalized
import java.util.Locale

/** Accurate statistics for one numeric column of a table. */
data class ColumnStat(
    val name: String,
    val count: Int,
    val sum: Double,
    val mean: Double,
    val min: Double,
    val max: Double,
    val minLabel: String?,
    val maxLabel: String?,
    /** Percentages / rates are averaged, never summed. */
    val isRate: Boolean,
)

data class TableInsight(
    val sheet: String,
    val headers: List<String>,
    val dataRows: Int,
    val numeric: List<ColumnStat>,
    val labelColumn: String?,
    val excludedTotalRows: Int,
)

/**
 * Summarises documents without internet or AI: picks the most informative sentences, figures,
 * recommendations, issues and action points, and computes exact statistics for spreadsheets.
 * Also used to give AI engines correct numbers (models are unreliable at arithmetic).
 */
object OfflineDocumentSummarizer {

    fun summarize(fileName: String, doc: ExtractedDocument, kind: DocKind): DocumentSummary {
        val insights = doc.tables.mapNotNull(::analyseTable)
        val sentences = sentences(doc.text, kind, doc.headings.toSet())
        val title = guessTitle(fileName, doc)
        val type = guessType(title + "\n" + doc.text, kind)

        val keyPoints = mutableListOf<String>()
        val keyFigures = mutableListOf<String>()
        insights.forEach { t ->
            keyPoints += describeSheet(t)
            keyFigures += t.numeric.take(6).map(::describeColumn)
        }

        val prose = sentences.filter { it.words in 6..80 }
        val recommendations = prose.filter { RECOMMEND.containsMatchIn(it.text) }.map { clip(it.text) }.distinct().take(6)
        val issues = prose.filter { ISSUE.containsMatchIn(it.text) }.map { clip(it.text) }.distinct().take(6)
        val actionSentences = prose.filter { ACTION.containsMatchIn(it.text) && !it.text.trimEnd().endsWith("?") }
        val actions = actionSentences.map { toAction(it.text) }
            .distinctBy { it.task.lowercase() }
            .take(8)

        if (kind != DocKind.EXCEL) {
            // Key points avoid repeating what the other sections already show, unless the document is short.
            val elsewhere = (recommendations + issues).toSet() + actionSentences.map { clip(it.text) }
            val fresh = prose.filter { clip(it.text) !in elsewhere }
            val ranked = prose.withIndex().sortedByDescending { (i, s) -> score(s, i, prose.size) }
            val picked = ranked.filter { it.value in fresh }.take(8).toMutableList()
            // Short documents: top up with the strongest remaining sentences so there are at least 4 points.
            ranked.filter { it.value !in fresh }.take((4 - picked.size).coerceAtLeast(0)).forEach { picked += it }
            val chosen = picked.sortedBy { it.index }.map { clip(it.value.text) }
            keyPoints += chosen
            prose.filter { FIGURE.containsMatchIn(it.text) && clip(it.text) !in chosen }
                .sortedByDescending { NUMBER.findAll(it.text).count() }
                .take(6)
                .forEach { keyFigures += clip(it.text) }
        }

        val overview = buildString {
            if (kind == DocKind.EXCEL || insights.isNotEmpty()) {
                val rows = insights.sumOf { it.dataRows }
                append("Spreadsheet with ${insights.size} sheet${if (insights.size == 1) "" else "s"} and ${fmt(rows.toDouble())} data rows. ")
                insights.maxByOrNull { it.dataRows }?.let { main ->
                    append("The main sheet \"${main.sheet}\" records ${main.headers.filter { it.isNotBlank() }.take(6).joinToString(", ")}")
                    if (main.labelColumn != null) append(" for each ${main.labelColumn}")
                    append(".")
                }
            } else if (prose.isEmpty()) {
                append("No readable text was found in this file. If it is a scanned PDF, choose an AI engine (Gemini reads scanned pages) in Settings and tap Re-analyse.")
            } else {
                append(prose.filter { it.words >= 8 }.ifEmpty { prose }.take(2).joinToString(" ") { it.text })
            }
        }.trim()

        return DocumentSummary(
            title = title,
            documentType = type,
            overview = overview,
            keyPoints = keyPoints,
            keyFigures = keyFigures,
            recommendations = recommendations,
            actionItems = actions,
            issues = issues,
            generatedBy = "Offline extraction",
        ).normalized()
    }

    // ------------------------------------------------------------------ tables

    fun analyseTable(t: SheetTable): TableInsight? {
        val rows = t.rows.filter { r -> r.any { it.isNotBlank() } }
        if (rows.size < 2) return null
        val width = rows.maxOf { it.size }
        // Header: first of the top 5 rows that is mostly filled and mostly text.
        val headerIndex = rows.take(5).indexOfFirst { r ->
            val filled = r.count { it.isNotBlank() }
            filled >= maxOf(2, width / 2) && r.count { it.isNotBlank() && parseNumber(it) == null } >= filled * 0.6
        }.let { if (it < 0) 0 else it }
        val headers = List(width) { c -> rows[headerIndex].getOrNull(c)?.trim().orEmpty().ifBlank { "Column ${c + 1}" } }
        var data = rows.drop(headerIndex + 1)
        val labelCol = (0 until width).firstOrNull { c ->
            val vals = data.mapNotNull { it.getOrNull(c)?.takeIf(String::isNotBlank) }
            vals.size >= data.size / 2 && vals.count { parseNumber(it) == null } >= vals.size * 0.8
        }
        val totalRows = data.filter { r -> r.any { TOTAL_ROW.matches(it.trim()) } }
        data = data - totalRows.toSet()

        val numeric = (0 until width).mapNotNull { c ->
            if (c == labelCol) return@mapNotNull null
            val cells = data.mapNotNull { r -> r.getOrNull(c)?.let { v -> parseNumber(v)?.let { it to r } } }
            val nonBlank = data.count { !it.getOrNull(c).isNullOrBlank() }
            if (cells.size < 2 || cells.size < nonBlank * 0.6) return@mapNotNull null
            val name = headers[c]
            if (ID_COLUMN.containsMatchIn(name)) return@mapNotNull null
            val values = cells.map { it.first }
            val label = { r: List<String> -> labelCol?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() } }
            val minPair = cells.minBy { it.first }
            val maxPair = cells.maxBy { it.first }
            ColumnStat(
                name = name,
                count = values.size,
                sum = values.sum(),
                mean = values.average(),
                min = minPair.first,
                max = maxPair.first,
                minLabel = label(minPair.second),
                maxLabel = label(maxPair.second),
                isRate = RATE_COLUMN.containsMatchIn(name) || cells.any { (it.second.getOrNull(c) ?: "").contains('%') },
            )
        }
        return TableInsight(t.name, headers, data.size, numeric, labelCol?.let { headers[it] }, totalRows.size)
    }

    fun describeSheet(t: TableInsight): String = buildString {
        append("Sheet \"${t.sheet}\": ${fmt(t.dataRows.toDouble())} records")
        append(" with columns ${t.headers.take(8).joinToString(", ")}")
        if (t.headers.size > 8) append(" and ${t.headers.size - 8} more")
        append(".")
        if (t.excludedTotalRows > 0) append(" (Total rows were left out of the calculations.)")
    }

    fun describeColumn(c: ColumnStat): String {
        val hi = c.maxLabel?.let { " – highest: $it (${fmt(c.max)})" } ?: " – highest ${fmt(c.max)}"
        val lo = c.minLabel?.let { ", lowest: $it (${fmt(c.min)})" } ?: ", lowest ${fmt(c.min)}"
        if (c.isRate) {
            // Excel stores 25% as 0.25; show it the way the sheet displays it.
            val asPercent = c.name.contains('%') || c.name.contains("percent", true)
            val scale = if (asPercent && c.max <= 1.0 && c.min >= -1.0) 100.0 else 1.0
            val unit = if (asPercent) "%" else ""
            fun r(v: Double) = fmtPrecise(v * scale) + unit
            val h = c.maxLabel?.let { " – highest: $it (${r(c.max)})" } ?: " – highest ${r(c.max)}"
            val l = c.minLabel?.let { ", lowest: $it (${r(c.min)})" } ?: ", lowest ${r(c.min)}"
            return "Average ${c.name}: ${r(c.mean)}$h$l"
        }
        return "Total ${c.name}: ${fmt(c.sum)} across ${c.count} entries (average ${fmt(c.mean)})$hi$lo"
    }

    /** Accurate figures written into AI prompts so the model doesn't do its own arithmetic. */
    fun statisticsForPrompt(tables: List<SheetTable>): String =
        tables.mapNotNull(::analyseTable).joinToString("\n") { t ->
            (listOf(describeSheet(t)) + t.numeric.map(::describeColumn)).joinToString("\n")
        }

    fun parseNumber(raw: String): Double? {
        val s = raw.trim().replace(",", "").replace("₦", "").replace("%", "").replace(Regex("^N(?=\\d)"), "").trim()
        if (s.isEmpty() || !s.any(Char::isDigit)) return null
        return s.toDoubleOrNull()
    }

    /** Like [fmt] but keeps two decimals for small values such as rates. */
    fun fmtPrecise(d: Double): String =
        if (kotlin.math.abs(d) >= 100) fmt(d)
        else "%,.2f".format(Locale.US, d).trimEnd('0').trimEnd('.')

    fun fmt(d: Double): String =
        if (kotlin.math.abs(d - Math.round(d)) < 1e-9 && kotlin.math.abs(d) < 1e15) "%,d".format(Locale.US, Math.round(d))
        else "%,.1f".format(Locale.US, d)

    // ------------------------------------------------------------------ prose

    data class Sentence(val text: String, val words: Int)

    private fun sentences(text: String, kind: DocKind, headings: Set<String>): List<Sentence> {
        if (kind == DocKind.EXCEL) return emptyList()
        return text.lines()
            .map { it.trim().removePrefix("•").trim() }
            .filter { it.isNotEmpty() && it !in headings && !isHeadingLike(it) && !it.contains(" | ") }
            .flatMap { SENTENCE_SPLIT.split(it) }
            .map { it.trim() }
            .filter { it.length > 15 }
            .distinct()
            .map { Sentence(it, it.split(Regex("\\s+")).size) }
    }

    private fun isHeadingLike(line: String): Boolean {
        val words = line.split(Regex("\\s+"))
        if (words.size > 10) return false
        if (line.endsWith('.') || line.endsWith(',')) return false
        return line == line.uppercase() || words.count { it.firstOrNull()?.isUpperCase() == true } >= words.size * 0.7 ||
            Regex("^(\\d+(\\.\\d+)*|[IVX]+|[A-Z])[.)]\\s").containsMatchIn(line)
    }

    private fun score(s: Sentence, index: Int, total: Int): Int {
        var score = 0
        score += KEYWORDS.findAll(s.text).count() * 2
        if (NUMBER.containsMatchIn(s.text)) score += 3
        if (RECOMMEND.containsMatchIn(s.text) || ISSUE.containsMatchIn(s.text)) score += 2
        if (index < total * 0.15) score += 2 // introductions carry the gist
        score += minOf(s.words, 30) / 8
        if (s.words > 60) score -= 2
        return score
    }

    private fun clip(text: String, maxWords: Int = 45): String {
        val words = text.split(Regex("\\s+"))
        return if (words.size <= maxWords) text else words.take(maxWords).joinToString(" ") + "…"
    }

    private fun toAction(text: String): SummaryActionItem {
        val clean = text.replace(Regex("""(?i)^(urgent|action|note)\s*[:\-–]\s*"""), "").trim()
        val ownerMatch = OWNER.find(clean)?.takeUnless { m -> m.groupValues[1].trim().split(' ').first().trimEnd('.') in NOT_NAMES }
        val owner = ownerMatch?.groupValues?.get(1)?.trim() ?: "TBD"
        val dueMatch = DUE.find(clean)
        val due = dueMatch?.groupValues?.get(2)?.trim()?.trimEnd('.', ',')?.replaceFirstChar { it.uppercase() } ?: "TBD"
        val priority = when {
            URGENT.containsMatchIn(text) -> "High"
            Regex("(?i)\\b(if possible|when convenient|nice to have|optional)\\b").containsMatchIn(text) -> "Low"
            else -> "Medium"
        }
        // "Dr. Amina Bello to lead a review … by Friday." → "Lead a review …"
        var task = clean
        if (ownerMatch != null && ownerMatch.range.first <= 2) task = clean.substring(ownerMatch.range.last + 1).trim()
        if (dueMatch != null && dueMatch.range.last >= clean.trimEnd('.', ' ').length - 1) {
            val cut = task.lastIndexOf(dueMatch.value)
            if (cut > 0) task = task.substring(0, cut)
        }
        task = task.trim().trimEnd('.', ',', ';').replaceFirstChar { it.uppercase() }
        if (task.split(' ').size < 3) task = clean.trimEnd('.')
        return SummaryActionItem(owner = owner, task = clip(task, 35), dueDate = due, priority = priority)
    }

    private fun guessTitle(fileName: String, doc: ExtractedDocument): String {
        val fromHeading = doc.headings.firstOrNull { it.split(' ').size in 2..14 && !it.startsWith("Sheet", true) }
        val fromFirstLine = doc.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.takeIf { it.split(' ').size in 2..14 && !it.contains(" | ") && !it.startsWith("Sheet:") }
        return fromHeading ?: fromFirstLine ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
    }

    private fun guessType(text: String, kind: DocKind): String {
        if (kind == DocKind.EXCEL) return "Spreadsheet / data table"
        val head = text.take(3000).lowercase()
        // The title (first line) is the strongest signal, then the opening of the document.
        return typeFrom(head.substringBefore('\n')) ?: typeFrom(head) ?: "Document"
    }

    private fun typeFrom(head: String): String? = when {
        "minutes of" in head || "in attendance" in head -> "Minutes of meeting"
        "terms of reference" in head -> "Terms of reference"
        "memorandum" in head || Regex("\\bmemo\\b").containsMatchIn(head) -> "Memo"
        "proposal" in head -> "Proposal"
        "situation report" in head || "sitrep" in head -> "Situation report"
        "report" in head -> "Report"
        "policy" in head -> "Policy document"
        "budget" in head -> "Budget"
        "dear sir" in head || "dear madam" in head || "yours faithfully" in head || "yours sincerely" in head -> "Letter"
        "guideline" in head -> "Guidelines"
        else -> null
    }

    // ------------------------------------------------------------------ vocabulary

    private val SENTENCE_SPLIT = Regex("""(?<!\b(?:Dr|Mr|Mrs|Ms|Engr|Prof|Barr|Hon|No|Fig|vs|etc|e\.g|i\.e)\.)(?<=[.!?])\s+(?=[A-Z0-9"'(])""")
    private val NUMBER = Regex("""\d""")
    private val FIGURE = Regex("""(?i)(\d[\d,.]*\s?%|₦\s?\d|\bN\d|\d[\d,.]*\s?(million|billion|thousand|cases|children|doses|persons|people|households|facilities|wards|lgas?|deaths|samples)\b|\b\d{2,}[\d,]*\b)""")
    private val KEYWORDS = Regex(
        """(?i)\b(objective|purpose|aim|goal|result|finding|conclusion|summary|key|important|significant|main|overall|total|increase\w*|decrease\w*|improv\w*|declin\w*|achiev\w*|target|coverage|performance|budget|cost|fund\w*|outbreak|cases|surveillance|vaccin\w*|immuni[sz]\w*|deadline|approved|agreed|resolved|priority|critical)\b"""
    )
    private val RECOMMEND = Regex("""(?i)\b(recommend\w*|should|must|need(s)? to|propos\w*|suggest\w*|advis\w*|way forward|it is (important|necessary|essential))\b""")
    private val ISSUE = Regex("""(?i)\b(challenge\w*|problem\w*|issue\w*|gap\w*|risk\w*|constraint\w*|delay\w*|shortage\w*|lack\w*|insufficient|inadequate|poor|low (coverage|turnout|uptake)|stock[- ]?out\w*|bottleneck\w*|concern\w*)\b""")
    private val ACTION = Regex("""(?i)\b((will|must|shall) (send|share|prepare|submit|conduct|organi[sz]e|follow|ensure|review|coordinate|distribute|train|visit|update|complete|provide|lead|draft|present|circulate)|[A-Z][a-z]+ to (lead|submit|prepare|send|share|conduct|follow|review|coordinate|draft|present|circulate)|responsible for|is to|are to|to be (done|completed|submitted|conducted) by|action:|deadline|by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|next|end of|\d{1,2}(st|nd|rd|th)?\s+\w+))\b""")
    private val OWNER = Regex("""\b((?:(?:Dr|Mr|Mrs|Ms|Engr|Prof|Hon|Alhaji|Hajiya|Mallam)\.?\s+)?[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)\s+(?:will|shall|must|is to|are to|should|to(?=\s+[a-z]))\b""")
    private val NOT_NAMES = setOf("The", "This", "That", "We", "They", "It", "Each", "All", "Every", "Team", "State", "Government", "Staff", "Members", "Partners", "LGA", "Ministry")
    private val DUE = Regex("""(?i)\b(by|before|on or before|no later than|latest by|deadline:?)\s+((?:the\s+)?(?:end of (?:the )?(?:day|week|month|year|quarter)|today|tomorrow|next \w+|(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|\d{1,2}(?:st|nd|rd|th)?(?: of)? (?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*(?:,? \d{4})?|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w* \d{1,2}(?:st|nd|rd|th)?(?:,? \d{4})?|\d{1,2}/\d{1,2}(?:/\d{2,4})?))""")
    private val URGENT = Regex("""(?i)\b(urgent(ly)?|immediately|asap|critical|without delay|top priority)\b""")
    private val TOTAL_ROW = Regex("""(?i)^(grand\s+)?(sub-?)?totals?:?$|^sum$""")
    private val ID_COLUMN = Regex("""(?i)^(s/?n|no\.?|#|id|code|serial|sn|year|month|week|phone|tel)$""")
    private val RATE_COLUMN = Regex("""(?i)(%|percent|rate|ratio|coverage|proportion|average|avg|score)""")
}
