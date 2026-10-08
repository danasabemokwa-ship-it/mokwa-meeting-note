package com.meetnotes.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Renders minutes to an A4 PDF with Android's built-in PdfDocument (no extra libraries). */
@Singleton
class PdfExporter @Inject constructor() {

    private val pageWidth = 595   // A4 in points
    private val pageHeight = 842
    private val margin = 50f
    private val contentWidth = (pageWidth - 2 * margin).toInt()
    private val accent = Color.rgb(0, 105, 110)

    private val titlePaint = textPaint(20f, bold = true, color = accent)
    private val metaPaint = textPaint(10f, color = Color.DKGRAY)
    private val headingPaint = textPaint(13f, bold = true, color = accent)
    private val bodyPaint = textPaint(10.5f)
    private val smallPaint = textPaint(9f, color = Color.rgb(60, 60, 60))
    private val footerPaint = textPaint(8f, color = Color.GRAY)
    private val linePaint = Paint().apply { color = accent; strokeWidth = 1.2f }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE; strokeWidth = 1.6f }

    fun write(file: File, blocks: List<DocBlock>) {
        val doc = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        val bottom = pageHeight - margin - 16f

        fun finishPage() {
            page?.let { p ->
                p.canvas.drawText("Mokwa Meeting Note · Page $pageNumber", margin, pageHeight - margin / 2, footerPaint)
                doc.finishPage(p)
            }
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page!!.canvas
            y = margin
        }

        fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()

        fun draw(text: String, paint: TextPaint, indent: Float = 0f, after: Float = 6f, before: Float = 0f): Float {
            val l = layout(text, paint, (contentWidth - indent).toInt())
            if (y + before + l.height > bottom) newPage() else y += before
            val top = y
            canvas!!.save()
            canvas!!.translate(margin + indent, y)
            l.draw(canvas!!)
            canvas!!.restore()
            y += l.height + after
            return top
        }

        newPage()
        for (block in blocks) {
            when (block) {
                is DocBlock.Title -> {
                    draw(block.text, titlePaint, after = 4f)
                    canvas!!.drawLine(margin, y, pageWidth - margin, y, linePaint)
                    y += 10f
                }
                is DocBlock.Meta -> draw("${block.label}: ${block.value}", metaPaint, after = 2f)
                is DocBlock.Heading -> {
                    // keep a heading together with at least a couple of lines below it
                    if (y + 60f > bottom) newPage()
                    draw(block.text, headingPaint, before = 12f, after = 6f)
                }
                is DocBlock.Paragraph -> draw(block.text, bodyPaint)
                is DocBlock.Bullet -> {
                    val top = draw(block.text, bodyPaint, indent = 14f, after = 4f)
                    canvas!!.drawCircle(margin + 5f, top + 7f, 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
                }
                is DocBlock.ActionRow -> {
                    val top = draw(block.task, bodyPaint, indent = 18f, after = 1f)
                    val box = RectF(margin + 2f, top + 2f, margin + 11f, top + 11f)
                    canvas!!.drawRect(box, boxPaint)
                    if (block.done) {
                        canvas!!.drawLine(box.left + 2f, box.centerY(), box.centerX() - 0.5f, box.bottom - 2f, tickPaint)
                        canvas!!.drawLine(box.centerX() - 0.5f, box.bottom - 2f, box.right - 1f, box.top + 1f, tickPaint)
                    }
                    draw("Owner: ${block.owner}   ·   Due: ${block.due}", metaPaint, indent = 18f, after = 7f)
                }
                is DocBlock.Small -> draw(block.text, smallPaint, after = 3f)
                DocBlock.PageBreak -> newPage()
            }
        }
        finishPage()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
}
