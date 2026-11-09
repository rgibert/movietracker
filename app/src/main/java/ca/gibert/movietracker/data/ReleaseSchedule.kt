package ca.gibert.movietracker.data

import java.time.LocalDate

/** The release windows the app cares about, in the order a film reaches them. */
enum class ReleaseKind(val label: String) {
    THEATRICAL("In theatres"),
    DIGITAL("Digital"),
    PHYSICAL("Blu-ray & DVD"),
    TV("On TV"),
    ;

    val isHome: Boolean
        get() = this != THEATRICAL
}

data class ReleaseEvent(
    val date: LocalDate,
    val kind: ReleaseKind,
)

/** Where a watchlist movie stands today. */
enum class Availability {
    /** A streaming, free or rental provider lists it in the user's region. */
    STREAMING,

    /** Released for home viewing (digital, disc or TV), with no provider data. */
    HOME,

    /** In cinemas, not yet released for home viewing. */
    THEATERS,

    /** Every known release date is still ahead. */
    UPCOMING,

    /** Trakt has no dates for it yet. */
    UNKNOWN,
    ;

    /** Whether the movie belongs on the To Watch list. */
    val isWatchable: Boolean
        get() = this == STREAMING || this == HOME || this == THEATERS
}

/**
 * After this long in cinemas a film is assumed to be out for home viewing even
 * when Trakt has no digital date for the region.
 */
internal const val THEATRICAL_WINDOW_DAYS = 120L

internal fun releaseKindOf(type: String?): ReleaseKind? = when (type?.lowercase()) {
    "theatrical", "limited" -> ReleaseKind.THEATRICAL
    "digital" -> ReleaseKind.DIGITAL
    "physical" -> ReleaseKind.PHYSICAL
    "tv" -> ReleaseKind.TV
    // Festival premieres and undated "unknown" rows don't make a film
    // watchable for a regular viewer.
    else -> null
}

internal fun parseReleaseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
}

/**
 * The earliest date of each release kind for the region. When Trakt has no
 * regional dates, the movie's own `released` date stands in as theatrical.
 */
fun releaseEvents(movie: Movie, releases: List<MovieRelease>): List<ReleaseEvent> {
    val events = releases
        .mapNotNull { release ->
            val kind = releaseKindOf(release.releaseType) ?: return@mapNotNull null
            val date = parseReleaseDate(release.releaseDate) ?: return@mapNotNull null
            ReleaseEvent(date, kind)
        }
        .groupBy { it.kind }
        .map { (_, sameKind) -> sameKind.minBy { it.date } }

    if (events.isNotEmpty()) return events.sortedWith(compareBy({ it.date }, { it.kind }))

    val fallback = parseReleaseDate(movie.released) ?: return emptyList()
    return listOf(ReleaseEvent(fallback, ReleaseKind.THEATRICAL))
}

fun availabilityOf(
    movie: Movie,
    releases: List<MovieRelease>,
    hasProviders: Boolean,
    today: LocalDate = LocalDate.now(),
): Availability {
    if (hasProviders) return Availability.STREAMING

    val events = releaseEvents(movie, releases)
    if (events.any { it.kind.isHome && !it.date.isAfter(today) }) return Availability.HOME

    val theatrical = events.firstOrNull { it.kind == ReleaseKind.THEATRICAL }
    if (theatrical != null && !theatrical.date.isAfter(today)) {
        val homeAhead = events.any { it.kind.isHome && it.date.isAfter(today) }
        val longOut = theatrical.date.plusDays(THEATRICAL_WINDOW_DAYS).isBefore(today)
        return if (longOut && !homeAhead) Availability.HOME else Availability.THEATERS
    }

    if (events.isNotEmpty()) return Availability.UPCOMING

    // No dates at all: anything from before this year has long been out.
    val year = movie.year
    return if (year != null && year < today.year) Availability.HOME else Availability.UNKNOWN
}

/**
 * The dates worth showing on Coming Up. Movies already out for home viewing
 * have none; a movie in cinemas shows only its home release dates.
 */
fun upcomingEvents(
    movie: Movie,
    releases: List<MovieRelease>,
    hasProviders: Boolean,
    today: LocalDate = LocalDate.now(),
): List<ReleaseEvent> {
    val availability = availabilityOf(movie, releases, hasProviders, today)
    if (availability == Availability.STREAMING || availability == Availability.HOME) {
        return emptyList()
    }
    return releaseEvents(movie, releases)
        .filter { !it.date.isBefore(today) }
        .filter { availability != Availability.THEATERS || it.kind.isHome }
}

/** The next upcoming home release, used as the To Watch hint for films in cinemas. */
fun nextHomeRelease(
    movie: Movie,
    releases: List<MovieRelease>,
    today: LocalDate = LocalDate.now(),
): ReleaseEvent? = releaseEvents(movie, releases)
    .filter { it.kind.isHome && it.date.isAfter(today) }
    .minByOrNull { it.date }

/**
 * Whether the release dates are worth asking Trakt for. Films from two or more
 * years ago are out everywhere that matters, so they skip the request.
 */
internal fun needsReleaseLookup(movie: Movie, today: LocalDate = LocalDate.now()): Boolean {
    val year = movie.year ?: return true
    return year >= today.year - 1
}

/**
 * Turns TMDB's provider buckets into one ordered list: subscription first,
 * then free (with or without ads), then rent and buy. Duplicate names, ad tiers
 * ("Netflix Standard with Ads") and add-on channels sold through another store
 * ("Starz Amazon Channel") are folded away so each service appears once.
 */
fun selectProviders(region: TmdbRegionProviders?): List<StreamingProvider> {
    if (region == null) return emptyList()

    val buckets = listOf(
        ProviderType.STREAM to region.flatrate,
        ProviderType.FREE to region.free + region.ads,
        ProviderType.RENT to region.rent,
        ProviderType.BUY to region.buy,
    )

    val seen = HashSet<String>()
    val result = ArrayList<StreamingProvider>()
    buckets.forEach { (type, providers) ->
        providers
            .sortedBy { it.displayPriority ?: Int.MAX_VALUE }
            .forEach { provider ->
                val name = normalizeProviderName(provider.providerName) ?: return@forEach
                if (seen.add("${type.name}:${name.lowercase()}")) {
                    result += StreamingProvider(name = name, type = type)
                }
            }
    }
    return result
}

private val addOnChannel = Regex("""\s+(amazon|apple tv|roku premium)\s+channel$""", RegexOption.IGNORE_CASE)
private val adTier = Regex("""\s+(standard|basic)?\s*with ads$""", RegexOption.IGNORE_CASE)

internal fun normalizeProviderName(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val withoutChannel = trimmed.replace(addOnChannel, "")
    return withoutChannel.replace(adTier, "").trim().ifEmpty { null }
}

/** Services the movie can be watched on without paying per title. */
fun List<StreamingProvider>.included(): List<StreamingProvider> =
    filter { it.type == ProviderType.STREAM || it.type == ProviderType.FREE }

/** The service to feature on a card: an included one first, else a store. */
fun List<StreamingProvider>.primary(): StreamingProvider? =
    firstOrNull { it.type == ProviderType.STREAM }
        ?: firstOrNull { it.type == ProviderType.FREE }
        ?: firstOrNull()
