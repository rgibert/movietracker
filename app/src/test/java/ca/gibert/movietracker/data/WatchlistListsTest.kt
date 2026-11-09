package ca.gibert.movietracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WatchlistListsTest {

    private val today = LocalDate.of(2026, 11, 9)

    private fun entry(
        id: Int,
        title: String,
        year: Int? = 2026,
        releases: List<Pair<String, String>> = emptyList(),
        providers: List<StreamingProvider> = emptyList(),
    ) = WatchlistMovie(
        movie = Movie(title = title, year = year, ids = TraktIds(trakt = id)),
        releases = releases.map { (type, date) ->
            MovieRelease(country = "us", releaseDate = date, releaseType = type)
        },
        providers = providers,
    )

    private val streaming = entry(
        id = 1,
        title = "Streaming",
        providers = listOf(
            StreamingProvider("Apple TV", ProviderType.RENT),
            StreamingProvider("Netflix", ProviderType.STREAM),
        ),
    )
    private val inTheatres = entry(
        id = 2,
        title = "In Theatres",
        releases = listOf("theatrical" to "2026-10-30", "digital" to "2026-12-20"),
    )
    private val upcoming = entry(
        id = 3,
        title = "Upcoming",
        releases = listOf("theatrical" to "2026-12-20", "digital" to "2027-02-01"),
    )
    private val undated = entry(id = 4, title = "Undated", year = 2027)
    private val items = listOf(streaming, inTheatres, upcoming, undated)

    @Test
    fun `to watch keeps released movies in watchlist order`() {
        val toWatch = buildToWatch(items, overrides = emptyMap(), today = today)
        assertEquals(listOf(1, 2), toWatch.map { it.movie.ids.trakt })
        assertEquals("Netflix", toWatch[0].service)
        assertEquals(Availability.THEATERS, toWatch[1].availability)
        assertEquals(LocalDate.of(2026, 12, 20), toWatch[1].nextHome?.date)
        assertNull(toWatch[0].nextHome)
    }

    @Test
    fun `a pinned service makes a movie watchable and wins the badge`() {
        val toWatch = buildToWatch(items, overrides = mapOf(3 to "Plex", 1 to "Max"), today = today)
        assertEquals(listOf(1, 2, 3), toWatch.map { it.movie.ids.trakt })
        assertEquals("Max", toWatch[0].service)
        assertEquals("Plex", toWatch[2].service)
    }

    @Test
    fun `coming up lists each future release and the undated movies`() {
        val comingUp = buildComingUp(items, overrides = emptyMap(), today = today)
        assertEquals(
            listOf(
                // Same day: theatrical sorts ahead of digital.
                Triple(3, ReleaseKind.THEATRICAL, LocalDate.of(2026, 12, 20)),
                Triple(2, ReleaseKind.DIGITAL, LocalDate.of(2026, 12, 20)),
                Triple(3, ReleaseKind.DIGITAL, LocalDate.of(2027, 2, 1)),
            ),
            comingUp.dated.map { Triple(it.movie.ids.trakt, it.event.kind, it.event.date) },
        )
        assertEquals(listOf(4), comingUp.undated.map { it.movie.ids.trakt })
    }

    @Test
    fun `featured service prefers pins then included providers`() {
        val providers = listOf(
            StreamingProvider("Apple TV", ProviderType.RENT),
            StreamingProvider("Tubi", ProviderType.FREE),
        )
        assertEquals("Plex", featuredService(9, providers, mapOf(9 to "Plex")))
        assertEquals("Tubi", featuredService(9, providers, emptyMap()))
        assertNull(featuredService(9, providers.take(1), emptyMap()))
    }
}
