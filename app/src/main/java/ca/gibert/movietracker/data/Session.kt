package ca.gibert.movietracker.data

data class SessionSnapshot(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0,
)

/**
 * In-memory view of the current credentials so OkHttp interceptors can read them
 * without blocking on DataStore for every request.
 */
class Session {

    @Volatile
    private var current = SessionSnapshot()

    fun snapshot(): SessionSnapshot = current

    fun update(stored: StoredAuth) {
        current = SessionSnapshot(
            clientId = stored.clientId.ifBlank { null },
            clientSecret = stored.clientSecret.ifBlank { null },
            accessToken = stored.accessToken.ifBlank { null },
            refreshToken = stored.refreshToken.ifBlank { null },
            expiresAt = stored.expiresAt,
        )
    }

    fun clearTokens() {
        current = current.copy(
            accessToken = null,
            refreshToken = null,
            expiresAt = 0,
        )
    }

    fun clearAll() {
        current = SessionSnapshot()
    }
}
