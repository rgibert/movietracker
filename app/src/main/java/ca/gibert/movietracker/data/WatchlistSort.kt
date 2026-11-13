package ca.gibert.movietracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.gibert.movietracker.util.parseTraktInstant
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** How the To Watch list is ordered. */
enum class WatchlistSort(val label: String) {
    ADDED_DESC("Date added · newest first"),
    ADDED_ASC("Date added · oldest first"),
    TITLE_ASC("Title · A to Z"),
    TITLE_DESC("Title · Z to A"),
    RELEASE_DESC("Release date · newest first"),
    RELEASE_ASC("Release date · oldest first"),
    ;

    companion object {
        val DEFAULT = ADDED_DESC

        fun fromName(name: String?): WatchlistSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Orders To Watch rows by [sort]. Rows missing the sort key (no added or
 * release date) go last in either direction, and ties fall back to title so
 * the order is stable between refreshes.
 */
fun List<ToWatchItem>.sortedFor(sort: WatchlistSort): List<ToWatchItem> {
    val byTitle = compareBy<ToWatchItem> { it.movie.title.lowercase() }
    val comparator: Comparator<ToWatchItem> = when (sort) {
        WatchlistSort.ADDED_DESC ->
            compareBy<ToWatchItem, Instant?>(nullsLast(reverseOrder())) { it.addedAt() }
        WatchlistSort.ADDED_ASC ->
            compareBy<ToWatchItem, Instant?>(nullsLast()) { it.addedAt() }
        WatchlistSort.TITLE_ASC -> byTitle
        WatchlistSort.TITLE_DESC -> byTitle.reversed()
        WatchlistSort.RELEASE_DESC ->
            compareBy<ToWatchItem, Long?>(nullsLast(reverseOrder())) { it.releaseDate()?.toEpochDay() }
        WatchlistSort.RELEASE_ASC ->
            compareBy<ToWatchItem, Long?>(nullsLast()) { it.releaseDate()?.toEpochDay() }
    }
    return sortedWith(comparator.then(byTitle))
}

private fun ToWatchItem.addedAt() = parseTraktInstant(entry.listedAt)

/** The movie's first release date, or 1 January of its year when that's all Trakt has. */
internal fun ToWatchItem.releaseDate(): LocalDate? =
    parseReleaseDate(movie.released) ?: movie.year?.let { LocalDate.of(it, 1, 1) }

private val Context.viewPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "view_prefs",
)

/** Remembers the chosen To Watch sort between launches. */
class WatchlistSortStore(private val context: Context) {

    suspend fun read(): WatchlistSort {
        val stored = context.viewPrefsDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()[KEY]
        return WatchlistSort.fromName(stored)
    }

    suspend fun set(sort: WatchlistSort) {
        context.viewPrefsDataStore.edit { it[KEY] = sort.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("to_watch_sort")
    }
}
