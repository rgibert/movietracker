package ca.gibert.movietracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TraktRepository(
    private val api: TraktApi,
    private val tmdbApi: TmdbApi,
    private val tmdbApiKey: String,
    private val cache: CacheStore,
    private val regionStore: RegionStore,
) {

    // Bumped after every successful mutation so list screens can tell that
    // their snapshot is stale even when the auto-refresh window has not
    // elapsed yet.
    private val mutationCounter = AtomicLong(0)

    val mutationVersion: Long
        get() = mutationCounter.get()

    /** Whether streaming providers can be looked up at all. */
    val hasProviderData: Boolean
        get() = tmdbApiKey.isNotBlank()

    @Volatile
    private var watchlistIds: Set<Int>? = null

    @Volatile
    private var ratingsSnapshot: Pair<Long, Map<Int, Int>>? = null

    private val lookupsMutex = Mutex()
    private var lookupsLoaded = false
    private val releaseLookups = ConcurrentHashMap<Int, CachedReleases>()
    private val providerLookups = ConcurrentHashMap<Int, CachedProviders>()

    // -----------------------------------------------------------------------
    // Watchlist (To Watch and Coming Up)
    // -----------------------------------------------------------------------

    suspend fun cachedWatchlist(): CachedWatchlist? = cache.readWatchlist()

    suspend fun refreshWatchlist(): CachedWatchlist = coroutineScope {
        val region = regionStore.read()
        val entries = fetchWatchlist()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        ensureLookupsLoaded()

        // Trakt allows 1,000 GET calls per five minutes; a handful in flight
        // keeps a long watchlist quick without bursting through that.
        val permits = Semaphore(LOOKUP_CONCURRENCY)
        val items = entries.map { entry ->
            async {
                val movie = requireNotNull(entry.movie)
                val releases = if (needsReleaseLookup(movie, today)) {
                    permits.withPermit { releasesFor(movie, region, now) }
                } else {
                    emptyList()
                }
                val worthProviderLookup = availabilityOf(
                    movie = movie,
                    releases = releases,
                    hasProviders = false,
                    today = today,
                ) != Availability.UPCOMING
                val providers = if (worthProviderLookup) {
                    permits.withPermit { providersFor(movie, region, now) }?.providers.orEmpty()
                } else {
                    emptyList()
                }
                WatchlistMovie(
                    movie = movie,
                    listedAt = entry.listedAt,
                    releases = releases,
                    providers = providers,
                )
            }
        }.awaitAll()

        persistLookups()
        watchlistIds = items.mapTo(HashSet()) { it.movie.ids.trakt }

        val snapshot = CachedWatchlist(items = items, region = region, fetchedAt = now)
        cache.writeWatchlist(snapshot)
        snapshot
    }

    suspend fun addToWatchlist(movie: Movie) {
        val response = api.addToWatchlist(movieRequest(movie.ids.trakt))
        checkAccepted(response)
        watchlistIds = watchlistIds?.plus(movie.ids.trakt)
        mutationCounter.incrementAndGet()
    }

    suspend fun removeFromWatchlist(movieId: Int) {
        val response = api.removeFromWatchlist(movieRequest(movieId))
        checkAccepted(response)
        watchlistIds = watchlistIds?.minus(movieId)
        mutationCounter.incrementAndGet()
    }

    /** Ids of movies on the watchlist, from memory, the saved snapshot, or Trakt. */
    suspend fun watchlistMovieIds(): Set<Int> {
        watchlistIds?.let { return it }
        val cached = cache.readWatchlist()
        if (cached != null) {
            return cached.items.mapTo(HashSet()) { it.movie.ids.trakt }.also { watchlistIds = it }
        }
        return fetchWatchlist()
            .mapNotNullTo(HashSet()) { it.movie?.ids?.trakt }
            .also { watchlistIds = it }
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    suspend fun searchMovies(query: String): List<Movie> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return api.searchMovies(query = trimmed, extended = EXTENDED, limit = SEARCH_LIMIT)
            .mapNotNull { it.movie }
            .distinctBy { it.ids.trakt }
    }

    // -----------------------------------------------------------------------
    // Diary (watch history + ratings)
    // -----------------------------------------------------------------------

    suspend fun cachedDiary(): CachedDiary? = cache.readDiary()

    suspend fun refreshDiary(): CachedDiary = coroutineScope {
        val history = async { fetchHistoryPage(1) }
        val ratings = async { fetchRatings() }
        val (entries, hasMore) = history.await()
        val snapshot = CachedDiary(
            entries = entries,
            ratings = ratings.await(),
            hasMore = hasMore,
            fetchedAt = System.currentTimeMillis(),
        )
        cache.writeDiary(snapshot)
        snapshot
    }

    suspend fun loadMoreDiary(current: CachedDiary): CachedDiary {
        val nextPage = current.entries.size / DIARY_PAGE_LIMIT + 1
        val (entries, hasMore) = fetchHistoryPage(nextPage)
        val knownIds = current.entries.mapTo(HashSet()) { it.id }
        val snapshot = current.copy(
            entries = current.entries + entries.filter { it.id !in knownIds },
            hasMore = hasMore,
        )
        cache.writeDiary(snapshot)
        return snapshot
    }

    // -----------------------------------------------------------------------
    // Movie detail
    // -----------------------------------------------------------------------

    suspend fun cachedMovieDetail(movieId: Int): MovieDetail? = cache.readMovieDetail(movieId)

    suspend fun cacheMovieDetail(detail: MovieDetail) =
        cache.writeMovieDetail(detail.movie.ids.trakt, detail)

    suspend fun refreshMovieDetail(movieId: Int): MovieDetail = coroutineScope {
        val region = regionStore.read()
        val movie = async { api.movie(movieId, extended = EXTENDED) }
        val history = async { api.historyForMovie(movieId, limit = HISTORY_FOR_MOVIE_LIMIT) }
        val rating = async { optional { ratingFor(movieId) } }
        val comments = async { optional { myCommentsFor(movieId) }.orEmpty() }
        val inWatchlist = async { optional { movieId in watchlistMovieIds() } ?: false }

        val loaded = movie.await()
        val now = System.currentTimeMillis()
        ensureLookupsLoaded()
        val releases = releasesFor(loaded, region, now)
        val providers = providersFor(loaded, region, now)
        persistLookups()

        val detail = MovieDetail(
            movie = loaded,
            releases = releases,
            providers = providers?.providers.orEmpty(),
            providerLink = providers?.link,
            inWatchlist = inWatchlist.await(),
            rating = rating.await(),
            history = history.await().sortedByDescending { it.watchedAt },
            comments = comments.await().sortedByDescending { it.createdAt },
            fetchedAt = now,
        )
        cache.writeMovieDetail(movieId, detail)
        detail
    }

    /** Logs a play: the diary entry. [watchedAt] defaults to now on Trakt's side. */
    suspend fun addPlay(movieId: Int, watchedAt: Instant) {
        val response = api.addToHistory(
            SyncMoviesRequest(
                listOf(SyncMovie(ids = TraktIds(trakt = movieId), watchedAt = watchedAt.toString())),
            ),
        )
        checkAccepted(response)
        mutationCounter.incrementAndGet()
    }

    suspend fun removePlay(historyId: Long) {
        val response = api.removeFromHistory(HistoryRemoveRequest(listOf(historyId)))
        if (!response.notFound?.ids.isNullOrEmpty()) {
            error("Trakt couldn't find that diary entry.")
        }
        mutationCounter.incrementAndGet()
    }

    /** Sets a 1–10 rating, or clears it when [rating] is null. */
    suspend fun setRating(movieId: Int, rating: Int?) {
        val response = if (rating == null) {
            api.removeRatings(movieRequest(movieId))
        } else {
            require(rating in 1..10) { "Ratings go from 1 to 10." }
            api.addRatings(
                SyncMoviesRequest(
                    listOf(
                        SyncMovie(
                            ids = TraktIds(trakt = movieId),
                            rating = rating,
                            ratedAt = Instant.now().toString(),
                        ),
                    ),
                ),
            )
        }
        checkAccepted(response)
        ratingsSnapshot = ratingsSnapshot?.let { (fetchedAt, ratings) ->
            fetchedAt to if (rating == null) ratings - movieId else ratings + (movieId to rating)
        }
        mutationCounter.incrementAndGet()
    }

    /**
     * Posts a comment on the movie. Trakt needs at least five words and turns
     * anything over 200 words into a review automatically.
     */
    suspend fun postReview(movieId: Int, text: String, spoiler: Boolean): Comment {
        val comment = api.postComment(
            CommentRequest(
                movie = SyncMovie(ids = TraktIds(trakt = movieId)),
                comment = text.trim(),
                spoiler = spoiler,
            ),
        )
        mutationCounter.incrementAndGet()
        return comment
    }

    suspend fun deleteReview(commentId: Long) {
        api.deleteComment(commentId)
        mutationCounter.incrementAndGet()
    }

    // -----------------------------------------------------------------------
    // Account
    // -----------------------------------------------------------------------

    suspend fun getProfile(): User =
        api.userSettings().user ?: error("Trakt didn't return an account profile.")

    suspend fun region(): String = regionStore.read()

    /** Changes the region; lookups for the old one are dropped with the cache. */
    suspend fun setRegion(region: String) {
        regionStore.set(region)
        clearCache()
    }

    suspend fun clearCache() {
        lookupsMutex.withLock {
            releaseLookups.clear()
            providerLookups.clear()
            lookupsLoaded = true
        }
        watchlistIds = null
        ratingsSnapshot = null
        cache.clear()
        mutationCounter.incrementAndGet()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private suspend fun fetchWatchlist(): List<WatchlistEntry> {
        val all = ArrayList<WatchlistEntry>()
        var page = 1
        while (page <= MAX_WATCHLIST_PAGES) {
            val response = api.watchlistMovies(extended = EXTENDED, page = page, limit = PAGE_LIMIT)
            if (!response.isSuccessful) throw HttpException(response)
            val body = response.body().orEmpty()
            all += body
            val pageCount = response.headers()["X-Pagination-Page-Count"]?.toIntOrNull() ?: break
            if (body.isEmpty() || page >= pageCount) break
            page++
        }
        return all.filter { it.movie != null }.distinctBy { it.movie?.ids?.trakt }
    }

    private suspend fun fetchHistoryPage(page: Int): Pair<List<HistoryEntry>, Boolean> {
        val response = api.movieHistory(extended = EXTENDED, page = page, limit = DIARY_PAGE_LIMIT)
        if (!response.isSuccessful) throw HttpException(response)
        val entries = response.body().orEmpty().filter { it.movie != null }
        val pageCount = response.headers()["X-Pagination-Page-Count"]?.toIntOrNull()
        val hasMore = if (pageCount != null) page < pageCount else entries.size >= DIARY_PAGE_LIMIT
        return entries to hasMore
    }

    private suspend fun fetchRatings(): Map<Int, Int> {
        val ratings = api.movieRatings()
            .mapNotNull { rated -> rated.movie?.let { it.ids.trakt to rated.rating } }
            .toMap()
        ratingsSnapshot = System.currentTimeMillis() to ratings
        return ratings
    }

    private suspend fun ratingFor(movieId: Int): Int? {
        val snapshot = ratingsSnapshot
        val ratings = if (snapshot != null &&
            System.currentTimeMillis() - snapshot.first < RATINGS_TTL_MS
        ) {
            snapshot.second
        } else {
            fetchRatings()
        }
        return ratings[movieId]
    }

    private suspend fun myCommentsFor(movieId: Int): List<Comment> =
        api.myMovieComments(page = 1, limit = COMMENTS_LIMIT)
            .filter { it.movie?.ids?.trakt == movieId }
            .map { it.comment }

    private suspend fun releasesFor(movie: Movie, region: String, now: Long): List<MovieRelease> {
        val id = movie.ids.trakt
        val cached = releaseLookups[id]?.takeIf { it.region == region }
        if (cached != null && now - cached.fetchedAt < RELEASES_TTL_MS) return cached.releases

        return try {
            val releases = api.releases(id, region.lowercase(Locale.ROOT))
            releaseLookups[id] = CachedReleases(region = region, releases = releases, fetchedAt = now)
            releases
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Release dates refine the lists; without them the movie's own
            // release date still places it.
            cached?.releases.orEmpty()
        }
    }

    private suspend fun providersFor(movie: Movie, region: String, now: Long): CachedProviders? {
        val tmdbId = movie.ids.tmdb ?: return null
        if (tmdbApiKey.isBlank()) return null

        val id = movie.ids.trakt
        val cached = providerLookups[id]?.takeIf { it.region == region }
        if (cached != null && now - cached.fetchedAt < PROVIDERS_TTL_MS) return cached

        return try {
            val response = tmdbApi.watchProviders(tmdbId, tmdbApiKey)
            val regional = response.results[region]
            val fresh = CachedProviders(
                region = region,
                providers = selectProviders(regional),
                link = regional?.link,
                fetchedAt = now,
            )
            providerLookups[id] = fresh
            fresh
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            cached
        }
    }

    private suspend fun ensureLookupsLoaded() = lookupsMutex.withLock {
        if (lookupsLoaded) return@withLock
        cache.readLookups()?.let { saved ->
            saved.releases.forEach { (id, value) -> releaseLookups.putIfAbsent(id, value) }
            saved.providers.forEach { (id, value) -> providerLookups.putIfAbsent(id, value) }
        }
        lookupsLoaded = true
    }

    private suspend fun persistLookups() {
        val snapshot = lookupsMutex.withLock {
            CachedLookups(
                releases = HashMap(releaseLookups),
                providers = HashMap(providerLookups),
            )
        }
        optional { cache.writeLookups(snapshot) }
    }

    private fun movieRequest(movieId: Int) =
        SyncMoviesRequest(listOf(SyncMovie(ids = TraktIds(trakt = movieId))))

    private fun checkAccepted(response: SyncResponse) {
        if (!response.notFound?.movies.isNullOrEmpty()) {
            error("Trakt didn't recognise that movie.")
        }
    }

    /** Runs [block], turning any failure other than cancellation into null. */
    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val EXTENDED = "full,images"
        const val PAGE_LIMIT = 100
        const val MAX_WATCHLIST_PAGES = 20
        const val SEARCH_LIMIT = 30
        const val DIARY_PAGE_LIMIT = 50
        const val HISTORY_FOR_MOVIE_LIMIT = 100
        const val COMMENTS_LIMIT = 100
        const val LOOKUP_CONCURRENCY = 4
        const val RELEASES_TTL_MS = 3 * 24 * 60 * 60 * 1000L
        const val PROVIDERS_TTL_MS = 24 * 60 * 60 * 1000L
        const val RATINGS_TTL_MS = 2 * 60 * 1000L
    }
}
