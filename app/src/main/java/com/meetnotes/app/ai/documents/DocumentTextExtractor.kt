package com.meetnotes.app.ai.documents

import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs

/** A sheet (Excel) or CSV table: first row is not assumed to be the header — see [TableInsight]. */
data class SheetTable(val name: String, val rows: List<List<String>>)

data class ExtractedDocument(
    /** Readable plain text (for Excel: a compact text rendering of every sheet). */
    val text: String,
    val tables: List<SheetTable> = emptyList(),
    val pageCount: Int? = null,
    val headings: List<String> = emptyList(),
) {
    val wordCount: Int get() = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
}

/**
 * Pulls text out of Word (.docx), Excel (.xlsx), CSV and plain-text files using only the JDK
 * (an .docx/.xlsx is a zip of XML files). PDF is handled separately by [PdfTextReader].
 */
object DocumentTextExtractor {

    private const val MAX_ROWS = 5_000
    private const val MAX_COLS = 60

    fun extractDocx(file: File): ExtractedDocument = ZipFile(file).use { zip ->
        val xml = zip.getEntry("word/document.xml")?.let { e -> zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } }
            ?: error("This doesn't look like a Word (.docx) file.")
        docxXmlToText(xml)
    }

    /** Separated from [extractDocx] so it can be unit-tested with a raw XML string. */
    fun docxXmlToText(xml: String): ExtractedDocument {
        val token = Regex(
            """<w:tr[ >]|</w:tr>|<w:tc[ >]|</w:tc>|<w:p[ >]|<w:p/>|</w:p>|<w:pStyle w:val="([^"]+)"|<w:numPr>|<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br[^>]*/>"""
        )
        val lines = mutableListOf<String>()
        val headings = mutableListOf<String>()
        val para = StringBuilder()
        var style = ""
        var listItem = false
        var inRow = false
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inCell = false

        fun endParagraph() {
            val text = para.toString().replace(Regex("\\s+"), " ").trim()
            para.setLength(0)
            if (text.isEmpty()) { style = ""; listItem = false; return }
            if (inCell) {
                if (cell.isNotEmpty()) cell.append(' ')
                cell.append(text)
            } else {
                val isHeading = style.startsWith("Heading", true) || style.equals("Title", true)
                if (isHeading) headings += text
                lines += when {
                    isHeading -> "\n$text"
                    listItem -> "• $text"
                    else -> text
                }
            }
            style = ""; listItem = false
        }

        for (m in token.findAll(xml)) {
            val v = m.value
            when {
                v.startsWith("<w:tr") -> { inRow = true; cells.clear() }
                v == "</w:tr>" -> {
                    inRow = false
                    val row = cells.map { it.trim() }
                    if (row.any { it.isNotEmpty() }) lines += row.joinToString(" | ")
                }
                v.startsWith("<w:tc") -> { inCell = true; cell.setLength(0) }
                v == "</w:tc>" -> { endParagraph(); inCell = false; if (inRow) cells += cell.toString() }
                v.startsWith("<w:pStyle") -> style = m.groupValues[1]
                v == "<w:numPr>" -> listItem = true
                v == "<w:tab/>" -> para.append('\t')
                v.startsWith("<w:t") -> para.append(decode(m.groupValues[2]))
                v.startsWith("<w:br") -> para.append(' ')
                v == "</w:p>" || v == "<w:p/>" -> endParagraph()
            }
        }
        endParagraph()
        val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return ExtractedDocument(text = text, headings = headings)
    }

    fun extractXlsx(file: File): ExtractedDocument = ZipFile(file).use { zip ->
        fun read(name: String): String? = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } }
        val workbook = read("xl/workbook.xml") ?: error("This doesn't look like an Excel (.xlsx) file.")
        val rels = read("xl/_rels/workbook.xml.rels").orEmpty()
        val shared = parseSharedStrings(read("xl/sharedStrings.xml").orEmpty())

        val targets = Regex("""<Relationship\b([^>]*)/?>""").findAll(rels).associate { m ->
            attr(m.groupValues[1], "Id") to attr(m.groupValues[1], "Target")
        }
        val sheets = Regex("""<sheet\b([^>]*)/?>""").findAll(workbook).mapNotNull { m ->
            val name = decode(attr(m.groupValues[1], "name"))
            val target = targets[attr(m.groupValues[1], "r:id")] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val sheetXml = read(path) ?: return@mapNotNull null
            SheetTable(name, parseSheet(sheetXml, shared))
        }.filter { t -> t.rows.any { r -> r.any { it.isNotBlank() } } }.toList()

        tablesToDocument(sheets)
    }

    fun extractCsv(text: String, name: String = "Data"): ExtractedDocument {
        val delimiter = listOf(',', ';', '\t').maxBy { d -> text.lineSequence().take(5).sumOf { line -> line.count { it == d } } }
        val rows = text.lineSequence().filter { it.isNotBlank() }.take(MAX_ROWS).map { parseCsvLine(it, delimiter).take(MAX_COLS) }.toList()
        return tablesToDocument(listOf(SheetTable(name, rows)))
    }

    fun extractPlain(text: String): ExtractedDocument {
        val cleaned = if (text.contains("<html", true) || text.contains("<body", true)) {
            decode(text.replace(Regex("(?is)<(script|style).*?</\\1>"), " ").replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h\\d>"), "\n").replace(Regex("<[^>]+>"), " "))
        } else text
        return ExtractedDocument(text = cleaned.replace(Regex("[ \\t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim())
    }

    // ------------------------------------------------------------------ helpers

    private fun tablesToDocument(sheets: List<SheetTable>): ExtractedDocument {
        val text = buildString {
            sheets.forEach { t ->
                appendLine("Sheet: ${t.name}")
                t.rows.filter { r -> r.any { it.isNotBlank() } }.take(400).forEach { r ->
                    appendLine(r.joinToString(" | ") { it.trim() }.trimEnd(' ', '|'))
                }
                appendLine()
            }
        }.trim()
        return ExtractedDocument(text = text, tables = sheets, headings = sheets.map { it.name })
    }

    private fun parseSharedStrings(xml: String): List<String> =
        Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            val body = si.groupValues[1].replace(Regex("""<rPh\b.*?</rPh>""", RegexOption.DOT_MATCHES_ALL), "")
            Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { decode(it.groupValues[1]) }
        }.toList()

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val cellRe = Regex("""<c\b([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
        val rowRe = Regex("""<row\b[^>]*?(?:/>|>(.*?)</row>)""", RegexOption.DOT_MATCHES_ALL)
        for (rowMatch in rowRe.findAll(xml)) {
            if (rows.size >= MAX_ROWS) break
            val cells = sortedMapOf<Int, String>()
            var nextCol = 0
            for (c in cellRe.findAll(rowMatch.groupValues[1])) {
                val attrs = c.groupValues[1]
                val body = c.groupValues[2]
                val ref = attr(attrs, "r")
                val col = if (ref.isNotEmpty()) columnIndex(ref) else nextCol
                nextCol = col + 1
                if (col >= MAX_COLS) continue
                val type = attr(attrs, "t")
                val v = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(::decode)
                val value = when (type) {
                    "s" -> v?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { decode(it.groupValues[1]) }
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> v?.let(::tidyNumber) ?: ""
                }
                cells[col] = value
            }
            if (cells.isEmpty()) { rows += emptyList<String>(); continue }
            val width = cells.lastKey() + 1
            rows += List(width) { cells[it] ?: "" }
        }
        // drop trailing empty rows
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.lastIndex)
        return rows
    }

    /** "12.000000000000002" → "12"; leaves non-numbers untouched. */
    private fun tidyNumber(raw: String): String {
        val d = raw.trim().toDoubleOrNull() ?: return raw
        return if (abs(d - Math.round(d)) < 1e-9 && abs(d) < 1e15) Math.round(d).toString()
        else "%.4f".format(java.util.Locale.US, d).trimEnd('0').trimEnd('.')
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun attr(attrs: String, name: String): String =
        Regex("""(?:^|\s)${Regex.escape(name)}="([^"]*)"""").find(attrs)?.groupValues?.get(1).orEmpty()

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> { out += sb.toString().trim(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    fun decode(s: String): String {
        if (!s.contains('&')) return s
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos|nbsp);""").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                e == "amp" -> "&"
                e == "lt" -> "<"
                e == "gt" -> ">"
                e == "quot" -> "\""
                e == "apos" -> "'"
                else -> " "
            }
        }
    }
}
