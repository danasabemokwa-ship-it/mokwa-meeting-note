package com.meetnotes.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meetnotes.app.ai.documents.DocumentAnalyzer
import com.meetnotes.app.ai.documents.DocumentTextExtractor
import com.meetnotes.app.ai.documents.ExtractedDocument
import com.meetnotes.app.ai.documents.PdfTextReader
import com.meetnotes.app.data.local.DocumentDao
import com.meetnotes.app.data.local.DocumentEntity
import com.meetnotes.app.domain.model.DocKind
import com.meetnotes.app.domain.model.DocStatus
import com.meetnotes.app.domain.model.DocumentItem
import com.meetnotes.app.domain.model.DocumentSummary
import com.meetnotes.app.util.Formatters
import com.meetnotes.app.work.ProcessDocumentWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Imported Word / PDF / Excel / text documents and their key-point summaries. */
@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentDao,
    private val json: Json,
    private val pdfReader: PdfTextReader,
    private val analyzer: DocumentAnalyzer,
    private val workManager: WorkManager,
) {
    private val dir get() = File(context.filesDir, "documents").apply { mkdirs() }

    fun observeAll(): Flow<List<DocumentItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observe(id: Long): Flow<DocumentItem?> = dao.observe(id).map { it?.toDomain() }
    suspend fun get(id: Long): DocumentItem? = dao.get(id)?.toDomain()

    /** Plain text extracted from the document (for "View text" and Copy). */
    suspend fun text(id: Long): String? = withContext(Dispatchers.IO) {
        dao.get(id)?.textPath?.let { File(it) }?.takeIf { it.exists() }?.readText()
    }

    /**
     * Copies the picked / shared file into app storage and starts analysing it.
     * Throws [IllegalArgumentException] with a user-readable message for unsupported files.
     */
    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Document"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }?.let { name = it }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        if (name == "Document") uri.lastPathSegment?.substringAfterLast('/')?.takeIf { '.' in it }?.let { name = it }
        val mime = resolver.getType(uri) ?: ""
        val ext = name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "doc" -> throw IllegalArgumentException("Old Word (.doc) files can't be read. Open it in Word or Google Docs and save it as .docx or PDF.")
            "xls" -> throw IllegalArgumentException("Old Excel (.xls) files can't be read. Save it as .xlsx or CSV and try again.")
            "ppt", "pptx" -> throw IllegalArgumentException("PowerPoint files aren't supported yet. Save the slides as PDF and add the PDF.")
        }
        if (size > MAX_BYTES) throw IllegalArgumentException("This file is larger than 50 MB. Please choose a smaller file.")
        val kind = DocKind.from(name, mime)
        if (kind == DocKind.TEXT && ext !in TEXT_EXTENSIONS && !mime.startsWith("text/")) {
            throw IllegalArgumentException("Unsupported file type. Add a Word (.docx), PDF, Excel (.xlsx), CSV or text file.")
        }

        val target = File(dir, "${System.currentTimeMillis()}_${Formatters.safeFileName(name)}")
        val copied = (resolver.openInputStream(uri) ?: throw IllegalArgumentException("Couldn't open this file.")).use { input ->
            target.outputStream().use { out -> copyLimited(input, out) }
        }
        if (copied > MAX_BYTES) {
            target.delete()
            throw IllegalArgumentException("This file is larger than 50 MB. Please choose a smaller file.")
        }
        val id = dao.insert(
            DocumentEntity(
                name = name.substringBeforeLast('.').ifBlank { name },
                kind = kind,
                mimeType = mime.ifBlank { mimeFor(ext) },
                localPath = target.absolutePath,
                sizeBytes = copied,
                createdAt = System.currentTimeMillis(),
                status = DocStatus.EXTRACTING,
            )
        )
        schedule(id)
        id
    }

    /** Pasted text (e.g. the body of an email) analysed like a document. */
    suspend fun importText(title: String, text: String): Long = withContext(Dispatchers.IO) {
        val target = File(dir, "${System.currentTimeMillis()}_${Formatters.safeFileName(title.ifBlank { "Pasted text" })}.txt")
        target.writeText(text)
        val id = dao.insert(
            DocumentEntity(
                name = title.ifBlank { "Pasted text" },
                kind = DocKind.TEXT,
                mimeType = "text/plain",
                localPath = target.absolutePath,
                sizeBytes = target.length(),
                createdAt = System.currentTimeMillis(),
                status = DocStatus.EXTRACTING,
            )
        )
        schedule(id)
        id
    }

    fun schedule(id: Long) {
        val request = OneTimeWorkRequestBuilder<ProcessDocumentWorker>()
            .setInputData(workDataOf(ProcessDocumentWorker.KEY_DOCUMENT_ID to id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(ProcessDocumentWorker.TAG)
            .build()
        workManager.enqueueUniqueWork("document_$id", ExistingWorkPolicy.REPLACE, request)
    }

    /** Re-run the summary (e.g. after adding an API key). */
    suspend fun reanalyse(id: Long) {
        dao.setStatus(id, DocStatus.EXTRACTING, null)
        schedule(id)
    }

    /** Extract → summarise. Called by [ProcessDocumentWorker]. */
    suspend fun process(id: Long) {
        val e = dao.get(id) ?: return
        try {
            dao.setStatus(id, DocStatus.EXTRACTING, null)
            val file = File(e.localPath)
            if (!file.exists()) throw IllegalStateException("The file is missing. Please add it again.")
            val doc = withContext(Dispatchers.IO) { extract(file, e.kind, e.name) }
            val textFile = File(dir, "${e.id}_text.txt")
            withContext(Dispatchers.IO) { textFile.writeText(doc.text) }
            dao.setText(id, textFile.absolutePath, metaFor(doc, e.kind))

            dao.setStatus(id, DocStatus.ANALYSING, null)
            val summary = analyzer.analyse(e.name, file, doc, e.kind)
            dao.setSummary(id, json.encodeToString(DocumentSummary.serializer(), summary))
            dao.setStatus(id, DocStatus.READY, null)
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (ex: Throwable) {
            val msg = when (ex) {
                is java.util.zip.ZipException -> "This file looks damaged or isn't really a ${e.kind.label} file."
                is OutOfMemoryError -> "This document is too large to read on the phone."
                else -> ex.message ?: ex.javaClass.simpleName
            }
            dao.setStatus(id, DocStatus.FAILED, msg)
        }
    }

    private fun extract(file: File, kind: DocKind, name: String): ExtractedDocument = when (kind) {
        DocKind.PDF -> pdfReader.read(file)
        DocKind.WORD -> DocumentTextExtractor.extractDocx(file)
        DocKind.EXCEL ->
            if (file.name.endsWith(".csv", true) || isProbablyText(file)) DocumentTextExtractor.extractCsv(file.readText(), name)
            else DocumentTextExtractor.extractXlsx(file)
        DocKind.TEXT -> DocumentTextExtractor.extractPlain(file.readText())
    }

    private fun metaFor(doc: ExtractedDocument, kind: DocKind): String = when {
        kind == DocKind.EXCEL -> {
            val rows = doc.tables.sumOf { t -> t.rows.count { r -> r.any { it.isNotBlank() } } }
            "${doc.tables.size} sheet${if (doc.tables.size == 1) "" else "s"} · ${"%,d".format(rows)} rows"
        }
        else -> listOfNotNull(
            doc.pageCount?.let { "$it page${if (it == 1) "" else "s"}" },
            "${"%,d".format(doc.wordCount)} words",
        ).joinToString(" · ")
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { e ->
            File(e.localPath).delete()
            e.textPath?.let { File(it).delete() }
        }
        workManager.cancelUniqueWork("document_$id")
        dao.delete(id)
    }

    /** The original file, for "Open original" / sharing. */
    suspend fun originalFile(id: Long): File? = dao.get(id)?.localPath?.let(::File)?.takeIf { it.exists() }

    private fun DocumentEntity.toDomain() = DocumentItem(
        id = id,
        name = name,
        kind = kind,
        mimeType = mimeType,
        localPath = localPath,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        status = status,
        meta = meta,
        summary = summaryJson?.let { runCatching { json.decodeFromString(DocumentSummary.serializer(), it) }.getOrNull() },
        errorMessage = errorMessage,
    )

    private fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return total
            out.write(buf, 0, n)
        }
        return total
    }

    private fun isProbablyText(file: File): Boolean {
        val head = file.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        return !(head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) // xlsx is a zip ("PK")
    }

    private fun mimeFor(ext: String) = when (ext) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" -> "text/csv"
        else -> "text/plain"
    }

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        private val TEXT_EXTENSIONS = setOf("txt", "md", "html", "htm", "json", "xml", "log")

        /** MIME types offered in the file picker. */
        val PICKER_MIME_TYPES = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv", "text/comma-separated-values", "text/plain", "text/*",
            "application/msword", "application/vnd.ms-excel",
        )
    }
}
