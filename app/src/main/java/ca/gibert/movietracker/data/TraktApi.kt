package ca.gibert.movietracker.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {

    @POST("oauth/device/code")
    suspend fun createDeviceCode(@Body body: DeviceCodeRequest): DeviceCodeResponse

    @POST("oauth/device/token")
    suspend fun pollDeviceToken(@Body body: DeviceTokenRequest): TokenResponse

    @POST("oauth/token")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): TokenResponse

    @POST("oauth/revoke")
    suspend fun revoke(@Body body: RevokeRequest)
}

interface TraktApi {

    // Paginated so long watchlists come back whole; the response headers
    // report the page count.
    @GET("sync/watchlist/movies/rank/asc")
    suspend fun watchlistMovies(
        @Query("extended") extended: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): Response<List<WatchlistEntry>>

    @POST("sync/watchlist")
    suspend fun addToWatchlist(@Body body: SyncMoviesRequest): SyncResponse

    @POST("sync/watchlist/remove")
    suspend fun removeFromWatchlist(@Body body: SyncMoviesRequest): SyncResponse

    @GET("movies/{id}")
    suspend fun movie(
        @Path("id") id: Int,
        @Query("extended") extended: String,
    ): Movie

    @GET("movies/{id}/releases/{country}")
    suspend fun releases(
        @Path("id") id: Int,
        @Path("country") country: String,
    ): List<MovieRelease>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("extended") extended: String,
        @Query("limit") limit: Int,
    ): List<SearchResult>

    @GET("sync/ratings/movies")
    suspend fun movieRatings(): List<RatedMovie>

    @POST("sync/ratings")
    suspend fun addRatings(@Body body: SyncMoviesRequest): SyncResponse

    @POST("sync/ratings/remove")
    suspend fun removeRatings(@Body body: SyncMoviesRequest): SyncResponse

    @GET("sync/history/movies")
    suspend fun movieHistory(
        @Query("extended") extended: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): Response<List<HistoryEntry>>

    @GET("sync/history/movies/{id}")
    suspend fun historyForMovie(
        @Path("id") id: Int,
        @Query("limit") limit: Int,
    ): List<HistoryEntry>

    @POST("sync/history")
    suspend fun addToHistory(@Body body: SyncMoviesRequest): SyncResponse

    @POST("sync/history/remove")
    suspend fun removeFromHistory(@Body body: HistoryRemoveRequest): SyncResponse

    @POST("comments")
    suspend fun postComment(@Body body: CommentRequest): Comment

    @DELETE("comments/{id}")
    suspend fun deleteComment(@Path("id") id: Long)

    @GET("users/me/comments/all/movies")
    suspend fun myMovieComments(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("include_replies") includeReplies: String = "false",
    ): List<UserComment>

    @GET("users/settings")
    suspend fun userSettings(): UserSettings
}

/** The one TMDB endpoint the app uses: JustWatch-backed "where to watch". */
interface TmdbApi {

    @GET("3/movie/{id}/watch/providers")
    suspend fun watchProviders(
        @Path("id") tmdbId: Int,
        @Query("api_key") apiKey: String,
    ): TmdbProvidersResponse
}
