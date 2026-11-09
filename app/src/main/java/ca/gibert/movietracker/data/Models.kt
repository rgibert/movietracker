package ca.gibert.movietracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------------------
// Trakt API models
// ---------------------------------------------------------------------------

@Serializable
data class TraktIds(
    val trakt: Int,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
)

@Serializable
data class Images(
    val fanart: List<String> = emptyList(),
    val poster: List<String> = emptyList(),
    val logo: List<String> = emptyList(),
    val clearart: List<String> = emptyList(),
    val banner: List<String> = emptyList(),
    val thumb: List<String> = emptyList(),
)

@Serializable
data class Movie(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds,
    val tagline: String? = null,
    val overview: String? = null,
    /** First release date (YYYY-MM-DD), usually the US theatrical date. */
    val released: String? = null,
    val runtime: Int? = null,
    val country: String? = null,
    val trailer: String? = null,
    val homepage: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val certification: String? = null,
    val images: Images? = null,
)

@Serializable
data class WatchlistEntry(
    val rank: Int? = null,
    val id: Long? = null,
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String? = null,
    val movie: Movie? = null,
)

/** One row of `/movies/:id/releases/:country`. */
@Serializable
data class MovieRelease(
    val country: String? = null,
    val certification: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    /** unknown, premiere, limited, theatrical, digital, physical or tv. */
    @SerialName("release_type") val releaseType: String? = null,
    val note: String? = null,
)

@Serializable
data class SearchResult(
    val type: String? = null,
    val score: Double? = null,
    val movie: Movie? = null,
)

@Serializable
data class RatedMovie(
    @SerialName("rated_at") val ratedAt: String? = null,
    val rating: Int,
    val type: String? = null,
    val movie: Movie? = null,
)

/** A play in the user's watch history; this is the app's "diary entry". */
@Serializable
data class HistoryEntry(
    val id: Long,
    @SerialName("watched_at") val watchedAt: String? = null,
    val action: String? = null,
    val type: String? = null,
    val movie: Movie? = null,
)

@Serializable
data class Comment(
    val id: Long,
    val comment: String,
    val spoiler: Boolean = false,
    val review: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("user_rating") val userRating: Int? = null,
)

@Serializable
data class UserComment(
    val type: String? = null,
    val movie: Movie? = null,
    val comment: Comment,
)

@Serializable
data class User(
    val username: String,
    val name: String? = null,
    val private: Boolean = false,
    val vip: Boolean = false,
    val images: UserImages? = null,
)

@Serializable
data class UserImages(
    val avatar: Avatar? = null,
)

@Serializable
data class Avatar(
    val full: String? = null,
)

@Serializable
data class UserSettings(
    val user: User? = null,
)

// ---------------------------------------------------------------------------
// Trakt write requests
// ---------------------------------------------------------------------------

/**
 * A movie reference for the /sync endpoints. Null fields are left out of the
 * JSON (the client is configured with `explicitNulls = false`).
 */
@Serializable
data class SyncMovie(
    val ids: TraktIds,
    @SerialName("watched_at") val watchedAt: String? = null,
    val rating: Int? = null,
    @SerialName("rated_at") val ratedAt: String? = null,
)

@Serializable
data class SyncMoviesRequest(
    val movies: List<SyncMovie>,
)

/** Removes specific plays from history by their history ids. */
@Serializable
data class HistoryRemoveRequest(
    val ids: List<Long>,
)

@Serializable
data class CommentRequest(
    val movie: SyncMovie,
    val comment: String,
    val spoiler: Boolean = false,
)

@Serializable
data class SyncCounts(
    val movies: Int = 0,
    val shows: Int = 0,
    val seasons: Int = 0,
    val episodes: Int = 0,
)

@Serializable
data class SyncNotFound(
    // Unmatched references may lack a Trakt id, so keep them untyped.
    val movies: List<JsonElement> = emptyList(),
    val ids: List<Long> = emptyList(),
)

@Serializable
data class SyncResponse(
    val added: SyncCounts? = null,
    val deleted: SyncCounts? = null,
    val existing: SyncCounts? = null,
    @SerialName("not_found") val notFound: SyncNotFound? = null,
)

// ---------------------------------------------------------------------------
// Trakt OAuth (device flow)
// ---------------------------------------------------------------------------

@Serializable
data class DeviceCodeRequest(
    @SerialName("client_id") val clientId: String,
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class DeviceTokenRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("grant_type") val grantType: String = "refresh_token",
)

@Serializable
data class RevokeRequest(
    val token: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
    val scope: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

// ---------------------------------------------------------------------------
// TMDB watch providers
// ---------------------------------------------------------------------------

@Serializable
data class TmdbProvidersResponse(
    val id: Int? = null,
    val results: Map<String, TmdbRegionProviders> = emptyMap(),
)

@Serializable
data class TmdbRegionProviders(
    val link: String? = null,
    val flatrate: List<TmdbProvider> = emptyList(),
    val free: List<TmdbProvider> = emptyList(),
    val ads: List<TmdbProvider> = emptyList(),
    val rent: List<TmdbProvider> = emptyList(),
    val buy: List<TmdbProvider> = emptyList(),
)

@Serializable
data class TmdbProvider(
    @SerialName("provider_id") val providerId: Int? = null,
    @SerialName("provider_name") val providerName: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int? = null,
)

// ---------------------------------------------------------------------------
// App models (cached as JSON snapshots)
// ---------------------------------------------------------------------------

/** How a provider offers the movie. */
@Serializable
enum class ProviderType {
    @SerialName("stream")
    STREAM,

    @SerialName("free")
    FREE,

    @SerialName("rent")
    RENT,

    @SerialName("buy")
    BUY,
}

@Serializable
data class StreamingProvider(
    val name: String,
    val type: ProviderType,
)

/** A watchlist movie with everything the To Watch and Coming Up lists need. */
@Serializable
data class WatchlistMovie(
    val movie: Movie,
    @SerialName("listed_at") val listedAt: String? = null,
    val releases: List<MovieRelease> = emptyList(),
    val providers: List<StreamingProvider> = emptyList(),
)

@Serializable
data class CachedWatchlist(
    val items: List<WatchlistMovie> = emptyList(),
    val region: String = "",
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

@Serializable
data class CachedReleases(
    val region: String,
    val releases: List<MovieRelease> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

@Serializable
data class CachedProviders(
    val region: String,
    val providers: List<StreamingProvider> = emptyList(),
    val link: String? = null,
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

/**
 * Per-movie release and provider lookups. They change slowly, so they are
 * kept between refreshes to keep the number of API calls down.
 */
@Serializable
data class CachedLookups(
    val releases: Map<Int, CachedReleases> = emptyMap(),
    val providers: Map<Int, CachedProviders> = emptyMap(),
)

@Serializable
data class CachedDiary(
    val entries: List<HistoryEntry> = emptyList(),
    /** Movie id → the user's 1–10 rating. */
    val ratings: Map<Int, Int> = emptyMap(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

@Serializable
data class MovieDetail(
    val movie: Movie,
    val releases: List<MovieRelease> = emptyList(),
    val providers: List<StreamingProvider> = emptyList(),
    @SerialName("provider_link") val providerLink: String? = null,
    @SerialName("in_watchlist") val inWatchlist: Boolean = false,
    /** The user's 1–10 rating, or null when unrated. */
    val rating: Int? = null,
    val history: List<HistoryEntry> = emptyList(),
    val comments: List<Comment> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)
