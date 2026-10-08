package com.meetnotes.app.export

/** Format-neutral description of the minutes, rendered by both [PdfExporter] and [DocxExporter]. */
sealed interface DocBlock {
    data class Title(val text: String) : DocBlock
    data class Meta(val label: String, val value: String) : DocBlock
    data class Heading(val text: String) : DocBlock
    data class Paragraph(val text: String) : DocBlock
    data class Bullet(val text: String) : DocBlock
    data class ActionRow(val task: String, val owner: String, val due: String, val done: Boolean) : DocBlock
    data class Small(val text: String) : DocBlock
    data object PageBreak : DocBlock
}
