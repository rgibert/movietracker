package ca.gibert.movietracker.data

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reacts to a 401 by refreshing the access token once and replaying the request.
 * Refreshes are single-flight, so parallel 401s only cause one token exchange.
 */
class TokenAuthenticator(
    private val session: Session,
    private val authRepository: AuthRepository,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        if (response.priorResponse != null) return null

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            .orEmpty()

        val currentToken = session.snapshot().accessToken
        if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        val refreshed = runBlocking { authRepository.refreshTokens() }
        if (!refreshed) return null

        val token = session.snapshot().accessToken ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}
