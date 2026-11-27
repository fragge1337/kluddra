package app.kluddra.android.ui.notes

import app.kluddra.android.ui.theme.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class NoteDateFormatterAndColorsTest {

    private val utc = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 27, 19, 30, 0, 0, utc)
        .toInstant().toEpochMilli()

    @Test
    fun noteColorPalette_hasTenDistinctEntriesIncludingNewPastels() {
        assertEquals(10, NoteColor.entries.size)
        assertEquals(10, NoteColor.entries.map { it.key }.toSet().size)
        assertEquals(NoteColor.MINT, NoteColor.fromKey("mint"))
        assertEquals(NoteColor.PEACH, NoteColor.fromKey("peach"))
        assertEquals(NoteColor.SAND, NoteColor.fromKey("sand"))
        assertEquals(NoteColor.LAVENDER, NoteColor.fromKey("unknown_key"))
        assertTrue(NoteColor.autoPalette.containsAll(listOf(NoteColor.MINT, NoteColor.PEACH, NoteColor.SAND)))
        assertFalse(NoteColor.autoPalette.contains(NoteColor.NEUTRAL))
    }

    @Test
    fun formatTimestamp_handlesTodayYesterdaySameYearAndEarlierYear() {
        val todayTs = ZonedDateTime.of(2026, 11, 27, 18, 45, 0, 0, utc)
            .toInstant().toEpochMilli()
        val yesterdayTs = ZonedDateTime.of(2026, 11, 26, 9, 15, 0, 0, utc)
            .toInstant().toEpochMilli()
        val sameYearTs = ZonedDateTime.of(2026, 5, 14, 12, 0, 0, 0, utc)
            .toInstant().toEpochMilli()
        val earlierYearTs = ZonedDateTime.of(2025, 11, 24, 12, 0, 0, 0, utc)
            .toInstant().toEpochMilli()

        assertEquals(
            "Idag 18:45",
            NoteDateFormatter.formatTimestamp(
                epochMillis = todayTs,
                todayWithTimeTemplate = "Idag %1\$s",
                yesterdayLabel = "Igår",
                is24Hour = true,
                nowMillis = now,
                zoneId = utc,
                locale = Locale.forLanguageTag("sv-SE")
            )
        )

        assertEquals(
            "Today 6:45 PM",
            NoteDateFormatter.formatTimestamp(
                epochMillis = todayTs,
                todayWithTimeTemplate = "Today %1\$s",
                yesterdayLabel = "Yesterday",
                is24Hour = false,
                nowMillis = now,
                zoneId = utc,
                locale = Locale.US
            )
        )

        assertEquals(
            "Igår",
            NoteDateFormatter.formatTimestamp(
                epochMillis = yesterdayTs,
                todayWithTimeTemplate = "Idag %1\$s",
                yesterdayLabel = "Igår",
                is24Hour = true,
                nowMillis = now,
                zoneId = utc,
                locale = Locale.forLanguageTag("sv-SE")
            )
        )

        val sameYearFormatted = NoteDateFormatter.formatTimestamp(
            epochMillis = sameYearTs,
            todayWithTimeTemplate = "Today %1\$s",
            yesterdayLabel = "Yesterday",
            is24Hour = true,
            nowMillis = now,
            zoneId = utc,
            locale = Locale.US
        )
        assertTrue("Expected '14 May', got '$sameYearFormatted'", sameYearFormatted.contains("14") && sameYearFormatted.contains("May"))
        assertFalse(sameYearFormatted.contains("2026"))

        val earlierYearFormatted = NoteDateFormatter.formatTimestamp(
            epochMillis = earlierYearTs,
            todayWithTimeTemplate = "Today %1\$s",
            yesterdayLabel = "Yesterday",
            is24Hour = true,
            nowMillis = now,
            zoneId = utc,
            locale = Locale.US
        )
        assertTrue("Expected year 2025 in '$earlierYearFormatted'", earlierYearFormatted.contains("24") && earlierYearFormatted.contains("2025"))
        assertEquals("", NoteDateFormatter.formatTimestamp(0L, "Today %1\$s", "Yesterday", true, now, utc))
    }

    @Test
    fun hasDistinctEditTime_requiresMoreThanSixtySecondsDifference() {
        assertFalse(NoteDateFormatter.hasDistinctEditTime(1_000_000L, 1_000_000L))
        assertFalse(NoteDateFormatter.hasDistinctEditTime(1_000_000L, 1_060_000L))
        assertTrue(NoteDateFormatter.hasDistinctEditTime(1_000_000L, 1_060_001L))
        assertFalse(NoteDateFormatter.hasDistinctEditTime(0L, 1_060_001L))
    }
}
