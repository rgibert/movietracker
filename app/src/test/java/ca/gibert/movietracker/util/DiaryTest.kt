package ca.gibert.movietracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DiaryTest {

    @Test
    fun `counts words the way Trakt does`() {
        assertEquals(0, wordCount("   "))
        assertEquals(5, wordCount("  one two\nthree   four five "))
    }

    @Test
    fun `today is stamped now and past days in the evening`() {
        val zone = ZoneId.of("America/Toronto")
        val now = Instant.parse("2026-11-09T15:30:00Z")
        val clock = Clock.fixed(now, zone)

        assertEquals(now, watchedAtFor(LocalDate.of(2026, 11, 9), clock))
        assertEquals(
            Instant.parse("2026-11-02T01:00:00Z"),
            watchedAtFor(LocalDate.of(2026, 11, 1), clock),
        )
    }
}
