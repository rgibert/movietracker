package ca.gibert.movietracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.serviceOverridesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "service_overrides",
)

/**
 * A service the user pinned for a movie: where they plan to watch it.
 *
 * Streaming data comes from TMDB when the app was built with an API key, but it
 * can be missing, out of date, or not match what the viewer actually has (a
 * Plex server, a channel bundled with their TV plan). [title] is kept so
 * Settings can list pins without a network round trip.
 */
@Serializable
data class ServiceOverride(
    val trakt: Int,
    val title: String,
    val service: String,
)

/**
 * Per-movie pinned services. This is a preference rather than cached data, so
 * clearing saved data leaves it alone; it is dropped when the account is
 * disconnected.
 */
class ServiceOverrideStore(
    private val context: Context,
    private val json: Json,
) {

    private val preferences: Flow<Preferences> = context.serviceOverridesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val overrides: Flow<List<ServiceOverride>> =
        preferences.map { decodeServiceOverrides(json, it[KEY]) }

    suspend fun read(): List<ServiceOverride> = overrides.first()

    /** Movie id → pinned service name. */
    suspend fun readMap(): Map<Int, String> = read().toOverrideMap()

    /** Pins [service] for [movie]; a blank name removes the pin instead. */
    suspend fun set(movie: Movie, service: String) {
        val name = service.trim()
        write { overrides ->
            val others = overrides.filterNot { it.trakt == movie.ids.trakt }
            if (name.isEmpty()) {
                others
            } else {
                others + ServiceOverride(trakt = movie.ids.trakt, title = movie.title, service = name)
            }
        }
    }

    suspend fun remove(movieId: Int) = write { overrides ->
        overrides.filterNot { it.trakt == movieId }
    }

    suspend fun clear() {
        context.serviceOverridesDataStore.edit { it.remove(KEY) }
    }

    private suspend fun write(transform: (List<ServiceOverride>) -> List<ServiceOverride>) {
        context.serviceOverridesDataStore.edit { preferences ->
            val updated = transform(decodeServiceOverrides(json, preferences[KEY]))
            if (updated.isEmpty()) {
                preferences.remove(KEY)
            } else {
                preferences[KEY] = encodeServiceOverrides(json, updated)
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("overrides")
    }
}

internal fun List<ServiceOverride>.toOverrideMap(): Map<Int, String> =
    asSequence()
        .filter { it.service.isNotBlank() }
        .associate { it.trakt to it.service.trim() }

/**
 * The stored payload is JSON in a preferences file, so a hand-edited or
 * truncated value must not take the screen down with it.
 */
internal fun decodeServiceOverrides(json: Json, raw: String?): List<ServiceOverride> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        json.decodeFromString(ListSerializer(ServiceOverride.serializer()), raw)
    }.getOrNull().orEmpty()
}

internal fun encodeServiceOverrides(json: Json, overrides: List<ServiceOverride>): String =
    json.encodeToString(ListSerializer(ServiceOverride.serializer()), overrides)
