package ca.gibert.movietracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class DatesTest {

    @Test
    fun `parses Trakt timestamps in both supported shapes`() {
        assertEquals(
            Instant.parse("2024-05-06T12:30:00Z"),
            parseTraktInstant("2024-05-06T12:30:00.000Z"),
        )
        assertEquals(
            Instant.parse("2024-05-06T12:30:00Z"),
            parseTraktInstant("2024-05-06T14:30:00+02:00"),
        )
        assertEquals(
            Instant.parse("2024-05-06T00:00:00Z"),
            parseTraktInstant("2024-05-06"),
        )
    }

    @Test
    fun `returns null for missing or invalid timestamps`() {
        assertNull(parseTraktInstant(null))
        assertNull(parseTraktInstant(""))
        assertNull(parseTraktInstant("not a date"))
    }

    @Test
    fun `isAired compares against the provided clock`() {
        val now = Instant.parse("2024-05-06T12:00:00Z")

        assertTrue(isAired("2024-05-06T11:59:59Z", now))
        assertTrue(isAired("2024-05-06T12:00:00Z", now))
        assertFalse(isAired("2024-05-06T12:00:01Z", now))
        assertTrue(isAired(null, now))
    }

    @Test
    fun `relative day labels handle the surrounding days`() {
        val today = LocalDate.of(2024, 5, 6)

        assertEquals("Today", formatRelativeDay(today, today))
        assertEquals("Tomorrow", formatRelativeDay(today.plusDays(1), today))
        assertEquals("Yesterday", formatRelativeDay(today.minusDays(1), today))
    }

    @Test
    fun `formats time in UTC for 12 and 24 hour clocks`() {
        val instant = Instant.parse("2024-05-06T13:05:00Z")

        assertEquals("13:05", formatTime(instant, is24Hour = true, zone = ZoneOffset.UTC))
        assertTrue(formatTime(instant, is24Hour = false, zone = ZoneOffset.UTC).startsWith("1:05"))
    }

    @Test
    fun `local date uses the supplied zone`() {
        val instant = Instant.parse("2024-05-06T23:30:00Z")

        assertEquals(LocalDate.of(2024, 5, 6), instant.localDate(ZoneOffset.UTC))
        assertEquals(LocalDate.of(2024, 5, 7), instant.localDate(ZoneOffset.ofHours(2)))
    }

    @Test
    fun `month calendar includes whole weeks around the month`() {
        val days = monthCalendarDays(YearMonth.of(2024, 5), DayOfWeek.MONDAY)

        assertEquals(LocalDate.of(2024, 4, 29), days.first())
        assertEquals(LocalDate.of(2024, 6, 2), days.last())
        assertEquals(0, days.size % 7)
        assertTrue(days.contains(LocalDate.of(2024, 5, 1)))
        assertTrue(days.contains(LocalDate.of(2024, 5, 31)))
        assertTrue(days.zipWithNext().all { (day, next) -> next == day.plusDays(1) })
    }

    @Test
    fun `month calendar honours the first day of the week`() {
        val sundayStart = monthCalendarDays(YearMonth.of(2024, 5), DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2024, 4, 28), sundayStart.first())
        assertEquals(DayOfWeek.SUNDAY, sundayStart.first().dayOfWeek)
    }

    @Test
    fun `month calendar handles leap february`() {
        val days = monthCalendarDays(YearMonth.of(2024, 2), DayOfWeek.MONDAY)

        assertTrue(days.contains(LocalDate.of(2024, 2, 29)))
        assertEquals(0, days.size % 7)
    }

    @Test
    fun `runtimes read as hours and minutes`() {
        assertEquals("1h 52m", formatRuntime(112))
        assertEquals("2h", formatRuntime(120))
        assertEquals("45m", formatRuntime(45))
        assertNull(formatRuntime(0))
        assertNull(formatRuntime(null))
    }

    @Test
    fun `near release dates get a relative label`() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("Today", relativeDaysLabel(today, today))
        assertEquals("Tomorrow", relativeDaysLabel(today.plusDays(1), today))
        assertEquals("In 5 days", relativeDaysLabel(today.plusDays(5), today))
        assertNull(relativeDaysLabel(today.plusDays(30), today))
        assertNull(relativeDaysLabel(today.minusDays(1), today))
    }
}
