package com.meetnotes.app.ai.documents

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the text layer of a PDF on the phone (no internet). Scanned PDFs have no text layer. */
@Singleton
class PdfTextReader @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile private var initialised = false

    fun read(file: File): ExtractedDocument {
        if (!initialised) {
            PDFBoxResourceLoader.init(context)
            initialised = true
        }
        PDDocument.load(file).use { pdf ->
            val pages = pdf.numberOfPages
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = minOf(pages, MAX_PAGES)
            }
            val raw = stripper.getText(pdf)
            return ExtractedDocument(text = tidy(raw), pageCount = pages)
        }
    }

    /** Joins lines that were broken mid-sentence by the PDF layout. */
    private fun tidy(raw: String): String {
        val out = StringBuilder()
        raw.replace("\r", "").lines().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> if (!out.endsWith("\n\n")) out.append("\n\n")
                out.isEmpty() || out.endsWith("\n") -> out.append(line)
                // previous line ended mid-sentence and this one continues it
                !out.last().let { it == '.' || it == ':' || it == ';' || it == '?' || it == '!' } &&
                    (line.first().isLowerCase() || line.first().isDigit()) -> {
                    if (out.endsWith("-")) out.setLength(out.length - 1) else out.append(' ')
                    out.append(line)
                }
                else -> out.append('\n').append(line)
            }
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    companion object {
        const val MAX_PAGES = 300
    }
}
