package app.kluddra.android.ui.notes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Compact, locale-aware date formatter for NoteCard footers and the NoteEditor metadata bar.
 * - Same day: "Today 18:45" (localized)
 * - Previous day: "Yesterday" (localized)
 * - Same year: "24 Nov"
 * - Earlier year: "24 Nov 2025"
 */
object NoteDateFormatter {

    fun formatTimestamp(
        epochMillis: Long,
        todayWithTimeTemplate: String,
        yesterdayLabel: String,
        is24Hour: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        if (epochMillis <= 0L) return ""
        val dt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val noteDate = dt.toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

        return when {
            noteDate == today -> {
                val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
                val timeStr = dt.format(DateTimeFormatter.ofPattern(timePattern, locale))
                String.format(locale, todayWithTimeTemplate, timeStr)
            }
            noteDate == today.minusDays(1) -> yesterdayLabel
            noteDate.year == today.year -> {
                dt.format(DateTimeFormatter.ofPattern("d MMM", locale))
            }
            else -> {
                dt.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
            }
        }
    }

    /**
     * Returns true when [updatedAt] differs meaningfully from [createdAt] (> 60 seconds),
     * so brand-new notes show only "Created ..." instead of redundant duplicate timestamps.
     */
    fun hasDistinctEditTime(createdAt: Long, updatedAt: Long): Boolean =
        createdAt > 0L && updatedAt > 0L && (updatedAt - createdAt) > 60_000L
}
