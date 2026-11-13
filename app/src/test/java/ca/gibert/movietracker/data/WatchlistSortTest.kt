package ca.gibert.movietracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistSortTest {

    private fun item(
        title: String,
        listedAt: String?,
        released: String? = null,
        year: Int? = null,
    ) = ToWatchItem(
        entry = WatchlistMovie(
            movie = Movie(
                title = title,
                year = year,
                released = released,
                ids = TraktIds(trakt = title.hashCode()),
            ),
            listedAt = listedAt,
        ),
        availability = Availability.STREAMING,
        service = null,
        nextHome = null,
    )

    private val alien = item("Alien", "2026-03-01T10:00:00.000Z", released = "1979-05-25")
    private val brazil = item("brazil", "2026-01-15T10:00:00.000Z", released = "1985-02-20")
    private val coco = item("Coco", "2026-06-10T10:00:00.000Z", year = 2017)
    private val dune = item("Dune", null, released = "2021-10-22")
    private val undated = item("Eraserhead", "2026-02-01T10:00:00.000Z")

    private val all = listOf(alien, brazil, coco, dune, undated)

    private fun List<ToWatchItem>.titles() = map { it.movie.title }

    @Test
    fun addedNewestFirstPutsUnknownLast() {
        assertEquals(
            listOf("Coco", "Alien", "Eraserhead", "brazil", "Dune"),
            all.sortedFor(WatchlistSort.ADDED_DESC).titles(),
        )
    }

    @Test
    fun addedOldestFirstPutsUnknownLast() {
        assertEquals(
            listOf("brazil", "Eraserhead", "Alien", "Coco", "Dune"),
            all.sortedFor(WatchlistSort.ADDED_ASC).titles(),
        )
    }

    @Test
    fun titleIgnoresCase() {
        assertEquals(
            listOf("Alien", "brazil", "Coco", "Dune", "Eraserhead"),
            all.sortedFor(WatchlistSort.TITLE_ASC).titles(),
        )
        assertEquals(
            listOf("Eraserhead", "Dune", "Coco", "brazil", "Alien"),
            all.sortedFor(WatchlistSort.TITLE_DESC).titles(),
        )
    }

    @Test
    fun releaseDateFallsBackToYearAndPutsUnknownLast() {
        assertEquals(
            listOf("Dune", "Coco", "brazil", "Alien", "Eraserhead"),
            all.sortedFor(WatchlistSort.RELEASE_DESC).titles(),
        )
        assertEquals(
            listOf("Alien", "brazil", "Coco", "Dune", "Eraserhead"),
            all.sortedFor(WatchlistSort.RELEASE_ASC).titles(),
        )
    }

    @Test
    fun unknownStoredNameFallsBackToDefault() {
        assertEquals(WatchlistSort.DEFAULT, WatchlistSort.fromName("BOGUS"))
        assertEquals(WatchlistSort.TITLE_DESC, WatchlistSort.fromName("TITLE_DESC"))
    }
}
