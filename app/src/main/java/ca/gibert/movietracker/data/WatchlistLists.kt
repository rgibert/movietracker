package ca.gibert.movietracker.data

import java.time.LocalDate

/** A To Watch row: a watchlist movie that can be watched now. */
data class ToWatchItem(
    val entry: WatchlistMovie,
    val availability: Availability,
    /** The pinned or first included service, if any. */
    val service: String?,
    /** For films still in cinemas, when they come home. */
    val nextHome: ReleaseEvent?,
) {
    val movie: Movie
        get() = entry.movie
}

/**
 * A Coming Up row: a watchlist movie with every upcoming release date it has,
 * so its theatrical, digital and disc releases share one row.
 */
data class ComingUpItem(
    val entry: WatchlistMovie,
    /** Upcoming releases, soonest first. Never empty. */
    val events: List<ReleaseEvent>,
) {
    init {
        require(events.isNotEmpty()) { "A Coming Up item needs at least one release" }
    }

    val movie: Movie
        get() = entry.movie

    /** The movie's next release. */
    val next: ReleaseEvent
        get() = events.first()

    /** The days this movie has a release on. */
    val dates: Set<LocalDate>
        get() = events.mapTo(LinkedHashSet()) { it.date }
}

data class ComingUp(
    /** One item per movie, ordered by its next release. */
    val dated: List<ComingUpItem>,
    /** Watchlist movies Trakt has no release date for yet. */
    val undated: List<WatchlistMovie>,
) {
    /**
     * Movies grouped by each day they have a release on. A movie with digital
     * and disc releases on different days is listed under both days.
     */
    fun byReleaseDate(): Map<LocalDate, List<ComingUpItem>> {
        val days = sortedMapOf<LocalDate, MutableList<ComingUpItem>>()
        dated.forEach { item ->
            item.dates.forEach { date -> days.getOrPut(date) { ArrayList() } += item }
        }
        return days
    }

    /** Movies grouped under the day of their next release only. */
    fun byNextRelease(): Map<LocalDate, List<ComingUpItem>> =
        dated.groupBy { it.next.date }.toSortedMap()
}

/**
 * Splits out the watchlist movies that are available today, keeping the
 * watchlist's own order. A pinned service counts as available: the user is
 * telling us where they can watch it.
 */
fun buildToWatch(
    items: List<WatchlistMovie>,
    overrides: Map<Int, String>,
    today: LocalDate = LocalDate.now(),
): List<ToWatchItem> = items.mapNotNull { entry ->
    val movieId = entry.movie.ids.trakt
    val pinned = overrides[movieId]
    val availability = availabilityOf(
        movie = entry.movie,
        releases = entry.releases,
        hasProviders = entry.providers.isNotEmpty() || pinned != null,
        today = today,
    )
    if (!availability.isWatchable) return@mapNotNull null
    ToWatchItem(
        entry = entry,
        availability = availability,
        service = featuredService(movieId, entry.providers, overrides),
        nextHome = if (availability == Availability.THEATERS) {
            nextHomeRelease(entry.movie, entry.releases, today)
        } else {
            null
        },
    )
}

fun buildComingUp(
    items: List<WatchlistMovie>,
    overrides: Map<Int, String>,
    today: LocalDate = LocalDate.now(),
): ComingUp {
    val dated = ArrayList<ComingUpItem>()
    val undated = ArrayList<WatchlistMovie>()

    items.forEach { entry ->
        val hasProviders = entry.providers.isNotEmpty() || entry.movie.ids.trakt in overrides
        val availability = availabilityOf(entry.movie, entry.releases, hasProviders, today)
        if (availability == Availability.UNKNOWN) {
            undated += entry
            return@forEach
        }
        val events = upcomingEvents(entry.movie, entry.releases, hasProviders, today)
            .sortedWith(compareBy({ it.date }, { it.kind }))
        if (events.isNotEmpty()) dated += ComingUpItem(entry, events)
    }

    return ComingUp(
        dated = dated.sortedWith(
            compareBy<ComingUpItem>({ it.next.date }, { it.next.kind }, { it.movie.title.lowercase() }),
        ),
        undated = undated.sortedBy { it.movie.title.lowercase() },
    )
}

/**
 * The service to show for a movie: the user's pin wins over TMDB's first
 * included (subscription or free) provider.
 */
fun featuredService(
    movieId: Int,
    providers: List<StreamingProvider>,
    overrides: Map<Int, String>,
): String? = overrides[movieId] ?: providers.included().firstOrNull()?.name
