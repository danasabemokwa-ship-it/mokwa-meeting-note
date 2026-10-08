package com.meetnotes.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val AUTO_TITLE = Regex("""^Meeting_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}(_\d+)?$""")

    fun isAutoTitle(title: String) = AUTO_TITLE.matches(title)

    fun dateTime(millis: Long): String =
        SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(millis))

    fun date(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

    fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(millis))

    /** 65_000 → "1:05", 3_725_000 → "1:02:05" */
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun safeFileName(name: String): String =
        name.replace(Regex("""[^\p{L}\p{N}._ -]"""), "_").trim().take(80).ifBlank { "meeting" }
}
