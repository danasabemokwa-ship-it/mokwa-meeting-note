package com.meetnotes.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Turns spoken/typed deadlines into a date (start of that day, local time):
 * "Friday", "next Tuesday", "tomorrow", "next tomorrow", "15 October", "15/10/2026",
 * "latest by Friday", "close of business Monday", "end of the month", "next week Monday".
 */
object DueDates {

    private val WEEKDAYS = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val PATTERNS = listOf(
        "yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d MMMM yyyy", "d MMM yyyy",
        "MMMM d, yyyy", "MMMM d yyyy", "MMM d, yyyy", "d MMMM", "d MMM", "MMMM d", "MMM d",
    )

    fun parse(raw: String?, reference: Long = System.currentTimeMillis()): Long? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim().lowercase(Locale.ENGLISH).trimEnd('.', ',')
            .removePrefix("latest by ").removePrefix("latest ").removePrefix("by ")
            .removePrefix("before ").removePrefix("on ").removePrefix("due ")
        val cob = Regex("^(close of business|cob)(\\s+on)?\\s*")
        val wasCob = cob.containsMatchIn(text)
        text = text.replace(cob, "").trim()
        val cal = Calendar.getInstance().apply { timeInMillis = reference }
        if (text.isBlank()) return if (wasCob) startOfDay(cal) else null
        if (text == "tbd" || text == "none" || text == "n/a") return null

        when (text) {
            "today", "tonight", "this evening", "end of day", "end of the day" -> return startOfDay(cal)
            "tomorrow" -> return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, 1) })
            "next tomorrow", "day after tomorrow" -> return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, 2) })
            "weekend", "this weekend" -> text = "saturday"
            "end of the week", "end of week", "end of this week", "this week" -> text = "friday"
            "next week" -> return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, 7) })
            "end of the month", "end of month", "end of this month", "month end", "this month" ->
                return startOfDay(cal.apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) })
        }

        // "next week monday" → the Monday of next week
        Regex("^next week (\\w+)$").find(text)?.let { m ->
            val wd = WEEKDAYS.indexOf(m.groupValues[1])
            if (wd >= 0) {
                val c = Calendar.getInstance().apply { timeInMillis = reference; add(Calendar.DAY_OF_YEAR, 7) }
                return weekdayOnOrAfter(startOfWeek(c), wd)
            }
        }
        // "next Monday" / "this Monday" → the coming Monday
        text = text.removePrefix("next ").removePrefix("this ")
        val wd = WEEKDAYS.indexOf(text)
        if (wd >= 0) {
            var diff = (wd + 1) - cal.get(Calendar.DAY_OF_WEEK)
            if (diff <= 0) diff += 7
            return startOfDay(cal.apply { add(Calendar.DAY_OF_YEAR, diff) })
        }

        val cleaned = text.replace(Regex("(\\d)(st|nd|rd|th)"), "$1").replace(" of ", " ").replace(Regex("\\s+"), " ")
        for (p in PATTERNS) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.ENGLISH).apply { isLenient = false }.parse(cleaned)
            }.getOrNull() ?: continue
            val result = Calendar.getInstance().apply { time = parsed }
            if (!p.contains("y")) {
                result.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                if (result.timeInMillis < reference - 30L * 86_400_000L) result.add(Calendar.YEAR, 1)
            }
            return startOfDay(result)
        }
        return null
    }

    /** "Fri, 16 Oct" style label for a parsed date. */
    fun label(millis: Long): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(java.util.Date(millis))

    /** Material3 DatePicker returns UTC midnight; convert to local start of that calendar day. */
    fun fromPickerUtc(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Local start-of-day → UTC midnight for pre-selecting the DatePicker. */
    fun toPickerUtc(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun startOfWeek(c: Calendar): Calendar = c.apply {
        // Monday-based week
        val dow = get(Calendar.DAY_OF_WEEK)
        val back = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        add(Calendar.DAY_OF_YEAR, -back)
    }

    private fun weekdayOnOrAfter(c: Calendar, wd: Int): Long {
        while (c.get(Calendar.DAY_OF_WEEK) != wd + 1) c.add(Calendar.DAY_OF_YEAR, 1)
        return startOfDay(c)
    }

    private fun startOfDay(c: Calendar): Long = c.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
