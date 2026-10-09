package com.meetnotes.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DocKind(val label: String) {
    PDF("PDF"), WORD("Word"), EXCEL("Excel"), TEXT("Text");

    companion object {
        fun from(fileName: String, mime: String?): DocKind {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                ext == "pdf" || mime == "application/pdf" -> PDF
                ext == "docx" || mime?.contains("wordprocessingml") == true -> WORD
                ext in setOf("xlsx", "csv") || mime?.contains("spreadsheetml") == true || mime == "text/csv" -> EXCEL
                else -> TEXT
            }
        }
    }
}

enum class DocStatus(val label: String) {
    EXTRACTING("Reading…"), ANALYSING("Summarising…"), READY("Ready"), FAILED("Needs attention");

    val isBusy: Boolean get() = this == EXTRACTING || this == ANALYSING
}

data class DocumentItem(
    val id: Long,
    val name: String,
    val kind: DocKind,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val status: DocStatus,
    /** e.g. "12 pages · 3,400 words" or "3 sheets · 420 rows". */
    val meta: String,
    val summary: DocumentSummary?,
    val errorMessage: String?,
)

/** Structured key points of an imported document. JSON names are the LLM contract (DocumentPrompts). */
@Serializable
data class DocumentSummary(
    val title: String = "",
    @SerialName("document_type") val documentType: String = "",
    val overview: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    @SerialName("key_figures") val keyFigures: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    @SerialName("action_items") val actionItems: List<SummaryActionItem> = emptyList(),
    @SerialName("issues_risks") val issues: List<String> = emptyList(),
    @SerialName("generated_by") val generatedBy: String = "",
)

fun DocumentSummary.normalized(): DocumentSummary {
    fun List<String>.clean() = map { it.trim().removePrefix("- ").removePrefix("• ").trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return copy(
        title = title.trim(),
        documentType = documentType.trim(),
        overview = overview.trim(),
        keyPoints = keyPoints.clean(),
        keyFigures = keyFigures.clean(),
        recommendations = recommendations.clean(),
        issues = issues.clean(),
        actionItems = actionItems.filter { it.task.isNotBlank() }.map {
            it.copy(
                owner = it.owner.trim().ifBlank { "TBD" },
                task = it.task.trim(),
                dueDate = it.dueDate.trim().ifBlank { "TBD" },
                priority = Priority.parse(it.priority).label,
            )
        },
    )
}
