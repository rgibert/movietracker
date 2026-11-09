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

/** A Coming Up row: one upcoming release date of a watchlist movie. */
data class ComingUpItem(
    val entry: WatchlistMovie,
    val event: ReleaseEvent,
) {
    val movie: Movie
        get() = entry.movie
}

data class ComingUp(
    /** Dated releases, soonest first. A movie can appear once per release kind. */
    val dated: List<ComingUpItem>,
    /** Watchlist movies Trakt has no release date for yet. */
    val undated: List<WatchlistMovie>,
)

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
        upcomingEvents(entry.movie, entry.releases, hasProviders, today).forEach { event ->
            dated += ComingUpItem(entry, event)
        }
    }

    return ComingUp(
        dated = dated.sortedWith(
            compareBy<ComingUpItem>({ it.event.date }, { it.event.kind }, { it.movie.title.lowercase() }),
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
