package ca.gibert.movietracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseScheduleTest {

    private val today = LocalDate.of(2026, 11, 9)

    private fun movie(year: Int? = 2026, released: String? = null) = Movie(
        title = "Test",
        year = year,
        ids = TraktIds(trakt = 1),
        released = released,
    )

    private fun release(type: String, date: String) =
        MovieRelease(country = "us", releaseDate = date, releaseType = type)

    @Test
    fun `earliest date of each kind wins and premieres are ignored`() {
        val events = releaseEvents(
            movie(),
            listOf(
                release("premiere", "2026-01-20"),
                release("theatrical", "2026-05-02"),
                release("limited", "2026-04-25"),
                release("digital", "2026-07-01"),
                release("digital", "2026-06-15"),
            ),
        )
        assertEquals(
            listOf(
                ReleaseEvent(LocalDate.of(2026, 4, 25), ReleaseKind.THEATRICAL),
                ReleaseEvent(LocalDate.of(2026, 6, 15), ReleaseKind.DIGITAL),
            ),
            events,
        )
    }

    @Test
    fun `falls back to the movie release date`() {
        val events = releaseEvents(movie(released = "2026-12-19"), emptyList())
        assertEquals(listOf(ReleaseEvent(LocalDate.of(2026, 12, 19), ReleaseKind.THEATRICAL)), events)
    }

    @Test
    fun `providers make a movie streaming whatever the dates say`() {
        val result = availabilityOf(movie(released = "2027-01-01"), emptyList(), hasProviders = true, today = today)
        assertEquals(Availability.STREAMING, result)
    }

    @Test
    fun `a past digital release is available at home`() {
        val result = availabilityOf(
            movie(),
            listOf(release("theatrical", "2026-08-01"), release("digital", "2026-10-01")),
            hasProviders = false,
            today = today,
        )
        assertEquals(Availability.HOME, result)
    }

    @Test
    fun `a recent theatrical release is in theatres`() {
        val result = availabilityOf(
            movie(),
            listOf(release("theatrical", "2026-10-20"), release("digital", "2026-12-15")),
            hasProviders = false,
            today = today,
        )
        assertEquals(Availability.THEATERS, result)
    }

    @Test
    fun `a long theatrical run with no home date counts as home`() {
        val result = availabilityOf(
            movie(),
            listOf(release("theatrical", "2026-03-01")),
            hasProviders = false,
            today = today,
        )
        assertEquals(Availability.HOME, result)
    }

    @Test
    fun `future dates only are upcoming and no dates are unknown`() {
        assertEquals(
            Availability.UPCOMING,
            availabilityOf(movie(), listOf(release("theatrical", "2027-02-01")), false, today),
        )
        assertEquals(Availability.UNKNOWN, availabilityOf(movie(year = 2027), emptyList(), false, today))
        assertEquals(Availability.HOME, availabilityOf(movie(year = 1999), emptyList(), false, today))
    }

    @Test
    fun `upcoming events skip the theatrical date once a film is in theatres`() {
        val releases = listOf(release("theatrical", "2026-10-20"), release("digital", "2026-12-15"))
        assertEquals(
            listOf(ReleaseEvent(LocalDate.of(2026, 12, 15), ReleaseKind.DIGITAL)),
            upcomingEvents(movie(), releases, hasProviders = false, today = today),
        )
        assertEquals(
            ReleaseEvent(LocalDate.of(2026, 12, 15), ReleaseKind.DIGITAL),
            nextHomeRelease(movie(), releases, today),
        )
    }

    @Test
    fun `movies available at home have no upcoming events`() {
        val releases = listOf(release("digital", "2026-10-01"), release("physical", "2026-12-01"))
        assertTrue(upcomingEvents(movie(), releases, hasProviders = false, today = today).isEmpty())
    }

    @Test
    fun `old films skip the release lookup`() {
        assertTrue(needsReleaseLookup(movie(year = 2026), today))
        assertTrue(needsReleaseLookup(movie(year = 2025), today))
        assertTrue(needsReleaseLookup(movie(year = null), today))
        assertFalse(needsReleaseLookup(movie(year = 2024), today))
    }

    @Test
    fun `release dates tolerate timestamps and junk`() {
        assertEquals(LocalDate.of(2026, 5, 1), parseReleaseDate("2026-05-01T00:00:00.000Z"))
        assertNull(parseReleaseDate("soon"))
        assertNull(parseReleaseDate(null))
    }

    @Test
    fun `providers are ordered, deduplicated and cleaned up`() {
        val providers = selectProviders(
            TmdbRegionProviders(
                flatrate = listOf(
                    TmdbProvider(providerName = "Max", displayPriority = 2),
                    TmdbProvider(providerName = "Netflix", displayPriority = 1),
                    TmdbProvider(providerName = "Netflix Standard with Ads", displayPriority = 3),
                    TmdbProvider(providerName = "Starz Amazon Channel", displayPriority = 4),
                ),
                ads = listOf(TmdbProvider(providerName = "Tubi TV")),
                rent = listOf(TmdbProvider(providerName = "Apple TV")),
                buy = listOf(TmdbProvider(providerName = "Apple TV")),
            ),
        )
        assertEquals(
            listOf(
                StreamingProvider("Netflix", ProviderType.STREAM),
                StreamingProvider("Max", ProviderType.STREAM),
                StreamingProvider("Starz", ProviderType.STREAM),
                StreamingProvider("Tubi TV", ProviderType.FREE),
                StreamingProvider("Apple TV", ProviderType.RENT),
                StreamingProvider("Apple TV", ProviderType.BUY),
            ),
            providers,
        )
        assertEquals("Netflix", providers.primary()?.name)
        assertTrue(selectProviders(null).isEmpty())
    }

    @Test
    fun `rental-only movies feature the store`() {
        val providers = listOf(StreamingProvider("Apple TV", ProviderType.RENT))
        assertEquals("Apple TV", providers.primary()?.name)
        assertTrue(providers.included().isEmpty())
    }
}
