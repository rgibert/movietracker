package ca.gibert.movietracker.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class TraktJsonTest {

    // Same settings as AppContainer.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `parses watchlist movies with extended info`() {
        val raw = """
            [{"rank":1,"id":101,"listed_at":"2026-10-01T12:00:00.000Z","notes":null,"type":"movie",
              "movie":{"title":"Dune: Part Three","year":2026,
                "ids":{"trakt":12345,"slug":"dune-part-three-2026","imdb":"tt31378509","tmdb":1170608},
                "tagline":"Long live the fighters.","overview":"…","released":"2026-12-18",
                "runtime":155,"country":"us","status":"post production","rating":0,"votes":0,
                "language":"en","genres":["science-fiction","adventure"],"certification":"PG-13",
                "images":{"poster":["walter-r2.trakt.tv/images/movies/000/012/345/posters/medium/abc.jpg.webp"]}}}]
        """.trimIndent()
        val items = json.decodeFromString(ListSerializer(WatchlistEntry.serializer()), raw)
        val movie = items.single().movie!!
        assertEquals(12345, movie.ids.trakt)
        assertEquals(1170608, movie.ids.tmdb)
        assertEquals("2026-12-18", movie.released)
        assertEquals(listOf("science-fiction", "adventure"), movie.genres)
        assertEquals(1, movie.images?.poster?.size)
    }

    @Test
    fun `parses release dates`() {
        val raw = """
            [{"country":"us","certification":"PG-13","release_date":"2026-12-18","release_type":"theatrical","note":null},
             {"country":"us","certification":"","release_date":"2027-02-02","release_type":"digital","note":"Apple TV"}]
        """.trimIndent()
        val releases = json.decodeFromString(ListSerializer(MovieRelease.serializer()), raw)
        assertEquals(listOf("theatrical", "digital"), releases.map { it.releaseType })
    }

    @Test
    fun `parses history, ratings and comments`() {
        val history = json.decodeFromString(
            ListSerializer(HistoryEntry.serializer()),
            """[{"id":1982346,"watched_at":"2026-11-01T20:00:00.000Z","action":"scrobble","type":"movie",
               "movie":{"title":"Heat","year":1995,"ids":{"trakt":1}}}]""",
        )
        assertEquals(1982346L, history.single().id)

        val ratings = json.decodeFromString(
            ListSerializer(RatedMovie.serializer()),
            """[{"rated_at":"2026-11-01T22:00:00.000Z","rating":9,"type":"movie",
               "movie":{"title":"Heat","year":1995,"ids":{"trakt":1}}}]""",
        )
        assertEquals(9, ratings.single().rating)

        val comments = json.decodeFromString(
            ListSerializer(UserComment.serializer()),
            """[{"type":"movie","movie":{"title":"Heat","year":1995,"ids":{"trakt":1}},
               "comment":{"id":267,"parent_id":0,"created_at":"2026-11-02T10:00:00.000Z",
                 "comment":"One of the great crime films ever made.","spoiler":false,"review":false,
                 "replies":0,"likes":0,"user_rating":9,"user":{"username":"me"}}}]""",
        )
        assertEquals(267L, comments.single().comment.id)
        assertEquals(9, comments.single().comment.userRating)
    }

    @Test
    fun `parses tmdb watch providers`() {
        val raw = """
            {"id":550,"results":{"CA":{"link":"https://www.themoviedb.org/movie/550/watch?locale=CA",
              "flatrate":[{"logo_path":"/a.jpg","provider_id":230,"provider_name":"Crave","display_priority":4}],
              "rent":[{"logo_path":"/b.jpg","provider_id":2,"provider_name":"Apple TV","display_priority":6}]}}}
        """.trimIndent()
        val response = json.decodeFromString(TmdbProvidersResponse.serializer(), raw)
        val ca = response.results["CA"]
        assertEquals("Crave", ca?.flatrate?.single()?.providerName)
        assertTrue(response.results["US"] == null)
        assertEquals(
            listOf(StreamingProvider("Crave", ProviderType.STREAM), StreamingProvider("Apple TV", ProviderType.RENT)),
            selectProviders(ca),
        )
    }

    @Test
    fun `sync requests leave out empty fields`() {
        val body = json.encodeToString(
            SyncMoviesRequest.serializer(),
            SyncMoviesRequest(listOf(SyncMovie(ids = TraktIds(trakt = 7)))),
        )
        assertEquals("""{"movies":[{"ids":{"trakt":7}}]}""", body)

        val rated = json.encodeToString(
            SyncMoviesRequest.serializer(),
            SyncMoviesRequest(listOf(SyncMovie(ids = TraktIds(trakt = 7), rating = 8))),
        )
        assertTrue(""""rating":8""" in rated)
        assertFalse("watched_at" in rated)
    }

    @Test
    fun `sync responses tolerate not found entries without trakt ids`() {
        val response = json.decodeFromString(
            SyncResponse.serializer(),
            """{"added":{"movies":0},"not_found":{"movies":[{"ids":{"imdb":"tt0000000"}}]}}""",
        )
        assertEquals(1, response.notFound?.movies?.size)
    }

    @Test
    fun `cached snapshots round trip`() {
        val snapshot = CachedWatchlist(
            items = listOf(
                WatchlistMovie(
                    movie = Movie(title = "Heat", year = 1995, ids = TraktIds(trakt = 1)),
                    providers = listOf(StreamingProvider("Netflix", ProviderType.STREAM)),
                ),
            ),
            region = "CA",
            fetchedAt = 42,
        )
        val decoded = json.decodeFromString(
            CachedWatchlist.serializer(),
            json.encodeToString(CachedWatchlist.serializer(), snapshot),
        )
        assertEquals(snapshot, decoded)

        val lookups = CachedLookups(releases = mapOf(1 to CachedReleases(region = "CA")))
        assertEquals(
            lookups,
            json.decodeFromString(CachedLookups.serializer(), json.encodeToString(CachedLookups.serializer(), lookups)),
        )
    }
}
