package ca.gibert.movietracker.data

import android.content.Context
import ca.gibert.movietracker.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
class AppContainer(context: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tokenStore = TokenStore(context)
    val session = Session()

    private val authApi: AuthApi = Retrofit.Builder()
        .baseUrl(AUTH_BASE_URL)
        .client(baseOkHttpClient())
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
        .create(AuthApi::class.java)

    val authRepository = AuthRepository(
        tokenStore = tokenStore,
        session = session,
        authApi = authApi,
        appScope = scope,
        defaultClientId = BuildConfig.DEFAULT_TRAKT_CLIENT_ID,
    )

    private val traktApi: TraktApi = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .client(
            baseOkHttpClient()
                .newBuilder()
                .addInterceptor(TraktHeadersInterceptor(session))
                .authenticator(TokenAuthenticator(session, authRepository))
                .build(),
        )
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
        .create(TraktApi::class.java)

    private val tmdbApi: TmdbApi = Retrofit.Builder()
        .baseUrl(TMDB_BASE_URL)
        .client(baseOkHttpClient())
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
        .create(TmdbApi::class.java)

    val cacheStore = CacheStore(context, json)

    val regionStore = RegionStore(context)

    val serviceOverrideStore = ServiceOverrideStore(context = context, json = json)

    val traktRepository = TraktRepository(
        api = traktApi,
        tmdbApi = tmdbApi,
        tmdbApiKey = BuildConfig.TMDB_API_KEY,
        cache = cacheStore,
        regionStore = regionStore,
    )

    init {
        scope.launch { authRepository.initialize() }
    }

    suspend fun signOut() {
        // Clear the cache before flipping the auth state, because flipping it
        // removes the settings screen (and its view model) from composition.
        runCatching { traktRepository.clearCache() }
        runCatching { serviceOverrideStore.clear() }
        authRepository.signOut()
    }

    suspend fun forgetApiApp() {
        runCatching { traktRepository.clearCache() }
        runCatching { serviceOverrideStore.clear() }
        authRepository.clearClientId()
    }

    private fun baseOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }
        return builder.build()
    }

    companion object {
        private const val AUTH_BASE_URL = "https://auth.trakt.tv/"
        private const val API_BASE_URL = "https://api.trakt.tv/"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
