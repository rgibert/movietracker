package ca.gibert.movietracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.Locale

private val Context.regionDataStore: DataStore<Preferences> by preferencesDataStore(name = "region")

/**
 * The country used for release dates and streaming providers. Defaults to the
 * device's country, which is right for almost everyone; Settings can change it.
 */
class RegionStore(private val context: Context) {

    suspend fun read(): String {
        val stored = context.regionDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()[KEY]
        return normalizeRegion(stored) ?: defaultRegion()
    }

    /** Saves [region]; a blank or invalid code goes back to the device default. */
    suspend fun set(region: String) {
        val code = normalizeRegion(region)
        context.regionDataStore.edit { preferences ->
            if (code == null) preferences.remove(KEY) else preferences[KEY] = code
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("country")

        fun defaultRegion(): String = normalizeRegion(Locale.getDefault().country) ?: "US"
    }
}

/** Two-letter ISO 3166 country code in upper case, or null if [value] isn't one. */
internal fun normalizeRegion(value: String?): String? {
    val code = value?.trim()?.uppercase(Locale.ROOT).orEmpty()
    return code.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
}
