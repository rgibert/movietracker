package ca.gibert.movietracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "trakt_auth")

data class StoredAuth(
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
)

class TokenStore(private val context: Context) {

    private val preferences: Flow<Preferences> = context.authDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    suspend fun read(): StoredAuth {
        val preferences = preferences.first()
        return StoredAuth(
            clientId = preferences[Keys.CLIENT_ID].orEmpty(),
            clientSecret = preferences[Keys.CLIENT_SECRET].orEmpty(),
            accessToken = preferences[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = preferences[Keys.REFRESH_TOKEN].orEmpty(),
            expiresAt = preferences[Keys.EXPIRES_AT] ?: 0,
        )
    }

    suspend fun saveClient(clientId: String, clientSecret: String?) {
        context.authDataStore.edit { preferences ->
            preferences[Keys.CLIENT_ID] = clientId
            if (clientSecret.isNullOrBlank()) {
                preferences.remove(Keys.CLIENT_SECRET)
            } else {
                preferences[Keys.CLIENT_SECRET] = clientSecret
            }
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        context.authDataStore.edit { preferences ->
            preferences[Keys.ACCESS_TOKEN] = accessToken
            preferences[Keys.REFRESH_TOKEN] = refreshToken
            preferences[Keys.EXPIRES_AT] = expiresAt
        }
    }

    suspend fun clearTokens() {
        context.authDataStore.edit { preferences ->
            preferences.remove(Keys.ACCESS_TOKEN)
            preferences.remove(Keys.REFRESH_TOKEN)
            preferences.remove(Keys.EXPIRES_AT)
        }
    }

    suspend fun clearAll() {
        context.authDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private object Keys {
        val CLIENT_ID = stringPreferencesKey("client_id")
        val CLIENT_SECRET = stringPreferencesKey("client_secret")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
    }
}
