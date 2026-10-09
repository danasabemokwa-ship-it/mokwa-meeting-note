package com.meetnotes.app.export

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes minutes as a Microsoft Word document (.docx, Office Open XML) using only the JDK zip API —
 * no Apache POI, so the APK stays small. The file opens in Word, Google Docs, WPS Office and
 * LibreOffice. Uses real Word styles (Title, Heading 1, bullets, a table for action items, a page-number
 * footer) so it can be edited and re-styled like any normal Word document.
 */
@Singleton
class DocxExporter @Inject constructor() {

    private val accent = "00696E"

    fun write(file: File, blocks: List<DocBlock>, title: String) {
        file.outputStream().use { write(it, blocks, title) }
    }

    fun write(out: OutputStream, blocks: List<DocBlock>, title: String) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("docProps/core.xml", coreProps(title))
            put("docProps/app.xml", APP_PROPS)
            put("word/_rels/document.xml.rels", DOCUMENT_RELS)
            put("word/styles.xml", styles())
            put("word/numbering.xml", NUMBERING)
            put("word/footer1.xml", FOOTER)
            put("word/document.xml", document(blocks))
        }
    }

    // ------------------------------------------------------------------ document body

    private fun document(blocks: List<DocBlock>): String {
        val body = StringBuilder()
        var i = 0
        while (i < blocks.size) {
            when (val b = blocks[i]) {
                is DocBlock.Title -> body.append(paragraph(b.text, style = "Title"))
                is DocBlock.Meta -> body.append(
                    """<w:p><w:pPr><w:pStyle w:val="Meta"/></w:pPr>${run(b.label + ": ", bold = true)}${run(b.value)}</w:p>"""
                )
                is DocBlock.Heading -> body.append(paragraph(b.text, style = "Heading1"))
                is DocBlock.Paragraph -> body.append(paragraph(b.text))
                is DocBlock.Bullet -> body.append(
                    """<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>${run(b.text)}</w:p>"""
                )
                is DocBlock.ActionRow -> {
                    // Consecutive action rows become one table.
                    val rows = mutableListOf<DocBlock.ActionRow>()
                    while (i < blocks.size && blocks[i] is DocBlock.ActionRow) {
                        rows += blocks[i] as DocBlock.ActionRow
                        i++
                    }
                    body.append(actionTable(rows))
                    continue
                }
                is DocBlock.Small -> body.append(paragraph(b.text, style = "Small"))
                DocBlock.PageBreak -> body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
            i++
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$W" xmlns:r="$R"><w:body>$body<w:sectPr><w:footerReference w:type="default" r:id="rIdFooter1"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/></w:sectPr></w:body></w:document>"""
    }

    private fun actionTable(rows: List<DocBlock.ActionRow>): String {
        // Column widths in twentieths of a point; total ≈ A4 text width (9638).
        val withPriority = rows.any { it.priority.isNotBlank() }
        val widths = if (withPriority) listOf(500, 3900, 1700, 1400, 1000, 1138) else listOf(560, 4618, 1820, 1540, 1100)
        val headers = if (withPriority) listOf("#", "Task", "Owner", "Due date", "Priority", "Status")
            else listOf("#", "Task", "Owner", "Due date", "Status")
        val sb = StringBuilder()
        sb.append("""<w:tbl><w:tblPr><w:tblStyle w:val="ActionTable"/><w:tblW w:w="5000" w:type="pct"/><w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="0" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/></w:tblPr><w:tblGrid>""")
        widths.forEach { sb.append("""<w:gridCol w:w="$it"/>""") }
        sb.append("</w:tblGrid>")
        // header row (repeats on every page)
        sb.append("<w:tr><w:trPr><w:tblHeader/></w:trPr>")
        headers.forEachIndexed { c, h ->
            sb.append(cell(widths[c], run(h, bold = true, color = "FFFFFF"), fill = accent))
        }
        sb.append("</w:tr>")
        rows.forEachIndexed { index, r ->
            val fill = if (index % 2 == 1) "EEF6F6" else null
            sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
            sb.append(cell(widths[0], run("${index + 1}"), fill))
            sb.append(cell(widths[1], run(r.task, strike = r.done), fill))
            sb.append(cell(widths[2], run(r.owner), fill))
            sb.append(cell(widths[3], run(r.due), fill))
            if (withPriority) {
                val pc = when (r.priority.lowercase()) { "high" -> "C62828"; "low" -> "5F6B7A"; else -> "9A6A00" }
                sb.append(cell(widths[4], run(r.priority, bold = r.priority.equals("high", true), color = pc), fill))
            }
            sb.append(
                cell(
                    widths.last(),
                    run(if (r.done) "☑ " else "☐ ", font = "Segoe UI Symbol") + run(if (r.done) "Done" else "Open", color = if (r.done) accent else null),
                    fill,
                )
            )
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
        sb.append("""<w:p><w:pPr><w:spacing w:after="0"/></w:pPr></w:p>""")
        return sb.toString()
    }

    private fun cell(width: Int, runs: String, fill: String?): String {
        val shading = fill?.let { """<w:shd w:val="clear" w:color="auto" w:fill="$it"/>""" }.orEmpty()
        return """<w:tc><w:tcPr><w:tcW w:w="$width" w:type="dxa"/>$shading</w:tcPr><w:p><w:pPr><w:spacing w:before="40" w:after="40"/></w:pPr>$runs</w:p></w:tc>"""
    }

    private fun paragraph(text: String, style: String? = null): String {
        val pPr = style?.let { """<w:pPr><w:pStyle w:val="$it"/></w:pPr>""" }.orEmpty()
        return "<w:p>$pPr${run(text)}</w:p>"
    }

    private fun run(
        text: String,
        bold: Boolean = false,
        strike: Boolean = false,
        color: String? = null,
        font: String? = null,
    ): String {
        val props = buildString {
            if (font != null) append("""<w:rFonts w:ascii="$font" w:hAnsi="$font" w:cs="$font"/>""")
            if (bold) append("<w:b/>")
            if (strike) append("<w:strike/>")
            if (color != null) append("""<w:color w:val="$color"/>""")
        }
        val rPr = if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else ""
        return """<w:r>$rPr<w:t xml:space="preserve">${xml(text)}</w:t></w:r>"""
    }

    // ------------------------------------------------------------------ package parts

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="$W">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="en-GB"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>
<w:pPr><w:pBdr><w:bottom w:val="single" w:sz="8" w:space="4" w:color="$accent"/></w:pBdr><w:spacing w:after="200"/></w:pPr>
<w:rPr><w:rFonts w:ascii="Calibri Light" w:hAnsi="Calibri Light"/><w:b/><w:color w:val="$accent"/><w:sz w:val="40"/><w:szCs w:val="40"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>
<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before="280" w:after="100"/><w:outlineLvl w:val="0"/></w:pPr>
<w:rPr><w:b/><w:color w:val="$accent"/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>
<w:style w:type="paragraph" w:customStyle="1" w:styleId="Meta"><w:name w:val="Meta"/><w:basedOn w:val="Normal"/><w:qFormat/>
<w:pPr><w:spacing w:after="40"/></w:pPr><w:rPr><w:color w:val="444444"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr></w:style>
<w:style w:type="paragraph" w:customStyle="1" w:styleId="Small"><w:name w:val="Small"/><w:basedOn w:val="Normal"/><w:qFormat/>
<w:pPr><w:spacing w:after="60"/></w:pPr><w:rPr><w:color w:val="555555"/><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/>
<w:pPr><w:spacing w:after="60"/><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/><w:basedOn w:val="Normal"/><w:rPr><w:color w:val="808080"/><w:sz w:val="16"/><w:szCs w:val="16"/></w:rPr></w:style>
<w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr><w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
<w:style w:type="table" w:customStyle="1" w:styleId="ActionTable"><w:name w:val="Action Table"/><w:basedOn w:val="TableNormal"/>
<w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="BFD9DA"/><w:left w:val="single" w:sz="4" w:space="0" w:color="BFD9DA"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="BFD9DA"/><w:right w:val="single" w:sz="4" w:space="0" w:color="BFD9DA"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="BFD9DA"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="BFD9DA"/></w:tblBorders></w:tblPr></w:style>
</w:styles>"""

    private fun coreProps(title: String): String {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>${xml(title)}</dc:title><dc:creator>Mokwa Meeting Note</dc:creator><cp:keywords>minutes of meeting</cp:keywords>
<dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>"""
    }

    /** Escapes XML special characters and drops characters that are illegal in XML 1.0. */
    private fun xml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' -> append(' ')
                ch < ' ' && ch != '\n' && ch != '\r' -> Unit
                ch == '￾' || ch == '￿' -> Unit
                else -> append(ch)
            }
        }
    }

    private companion object {
        const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""

        const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

        const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
<Relationship Id="rIdFooter1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
</Relationships>"""

        const val APP_PROPS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Mokwa Meeting Note</Application></Properties>"""

        const val NUMBERING = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/>
<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:color w:val="00696E"/></w:rPr></w:lvl>
</w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
</w:numbering>"""

        const val FOOTER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:pStyle w:val="Footer"/><w:jc w:val="center"/></w:pPr>
<w:r><w:t xml:space="preserve">Mokwa Meeting Note · Page </w:t></w:r>
<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r>
<w:r><w:t xml:space="preserve"> of </w:t></w:r>
<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> NUMPAGES </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r>
</w:p></w:ftr>"""
    }
}
