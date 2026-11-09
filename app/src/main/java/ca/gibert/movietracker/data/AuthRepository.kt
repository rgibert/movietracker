package ca.gibert.movietracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

sealed interface AuthState {
    data object Loading : AuthState
    data object NeedsClientId : AuthState
    data class LoggedOut(val clientId: String, val message: String? = null) : AuthState
    data class LoggedIn(val clientId: String) : AuthState
}

sealed interface DeviceLoginState {
    data object Idle : DeviceLoginState
    data object Starting : DeviceLoginState
    data class Waiting(
        val userCode: String,
        val verificationUrl: String,
        val expiresAt: Long,
    ) : DeviceLoginState

    data class Failed(val message: String) : DeviceLoginState
}

class AuthRepository(
    private val tokenStore: TokenStore,
    private val session: Session,
    private val authApi: AuthApi,
    private val appScope: CoroutineScope,
    private val defaultClientId: String = "",
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _deviceLogin = MutableStateFlow<DeviceLoginState>(DeviceLoginState.Idle)
    val deviceLogin: StateFlow<DeviceLoginState> = _deviceLogin.asStateFlow()

    private val refreshMutex = Mutex()
    private var pollJob: Job? = null

    suspend fun initialize() {
        var stored = tokenStore.read()
        if (stored.clientId.isBlank() && defaultClientId.isNotBlank()) {
            // First launch with a bundled app identity: skip the "paste your
            // Client ID" step and go straight to connecting the account.
            tokenStore.saveClient(defaultClientId, null)
            stored = stored.copy(clientId = defaultClientId)
        }
        session.update(stored)
        val clientId = stored.clientId.ifBlank { null }
        _state.value = when {
            clientId == null -> AuthState.NeedsClientId
            stored.accessToken.isBlank() || stored.refreshToken.isBlank() ->
                AuthState.LoggedOut(clientId)

            else -> AuthState.LoggedIn(clientId)
        }
    }

    suspend fun saveClientId(clientId: String, clientSecret: String?) {
        val trimmed = clientId.trim()
        if (trimmed.isBlank()) return

        val existing = tokenStore.read()
        val cleanedSecret = clientSecret?.trim()?.ifBlank { null }
            ?: existing.clientSecret.ifBlank { null }
        val sameApp = existing.clientId == trimmed &&
            existing.clientSecret.ifBlank { null } == cleanedSecret

        if (!sameApp) tokenStore.clearTokens()
        tokenStore.saveClient(trimmed, cleanedSecret)
        session.update(tokenStore.read())
        _deviceLogin.value = DeviceLoginState.Idle

        _state.value = if (sameApp && !session.snapshot().accessToken.isNullOrBlank()) {
            AuthState.LoggedIn(trimmed)
        } else {
            AuthState.LoggedOut(trimmed)
        }
    }

    suspend fun clearClientId() {
        cancelDeviceLogin()
        tokenStore.clearAll()
        session.clearAll()
        _deviceLogin.value = DeviceLoginState.Idle
        _state.value = AuthState.NeedsClientId
    }

    fun startDeviceLogin() {
        val snapshot = session.snapshot()
        val clientId = snapshot.clientId ?: run {
            _state.value = AuthState.NeedsClientId
            return
        }

        cancelDeviceLogin()
        _deviceLogin.value = DeviceLoginState.Starting

        pollJob = appScope.launch {
            try {
                val code = authApi.createDeviceCode(DeviceCodeRequest(clientId))
                val expiresAt = System.currentTimeMillis() + code.expiresIn * 1000L
                _deviceLogin.value = DeviceLoginState.Waiting(
                    userCode = code.userCode,
                    verificationUrl = code.verificationUrl,
                    expiresAt = expiresAt,
                )
                pollForToken(clientId, snapshot.clientSecret, code, expiresAt)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _deviceLogin.value = DeviceLoginState.Failed(deviceError(error))
            }
        }
    }

    private suspend fun pollForToken(
        clientId: String,
        clientSecret: String?,
        code: DeviceCodeResponse,
        expiresAt: Long,
    ) {
        var intervalSeconds = code.interval.coerceAtLeast(5)

        while (currentCoroutineContext().isActive && System.currentTimeMillis() < expiresAt) {
            delay(intervalSeconds * 1000L)
            if (System.currentTimeMillis() >= expiresAt) break

            try {
                val token = authApi.pollDeviceToken(
                    DeviceTokenRequest(
                        code = code.deviceCode,
                        clientId = clientId,
                        clientSecret = clientSecret,
                    ),
                )
                persistTokens(token)
                _deviceLogin.value = DeviceLoginState.Idle
                return
            } catch (error: HttpException) {
                when (error.code()) {
                    400, 409 -> Unit // Still waiting for the user to approve the code.
                    404 -> throw DeviceAuthException("That code is no longer valid. Please try again.")
                    410 -> throw DeviceAuthException("The code expired. Please try again.")
                    418 -> throw DeviceAuthException("Access was denied on Trakt.")
                    429 -> intervalSeconds += 5
                    else -> throw DeviceAuthException(
                        "Trakt returned HTTP ${error.code()} while connecting.",
                    )
                }
            } catch (error: IOException) {
                // Keep polling through transient network hiccups until the code expires.
            }
        }

        _deviceLogin.value = DeviceLoginState.Failed("The code expired. Please try again.")
    }

    suspend fun ensureFreshToken(): Boolean {
        val snapshot = session.snapshot()
        if (snapshot.accessToken.isNullOrBlank()) return false
        if (snapshot.expiresAt > System.currentTimeMillis() + 60_000) return true
        return refreshTokens()
    }

    suspend fun refreshTokens(): Boolean = refreshMutex.withLock {
        val snapshot = session.snapshot()
        if (!snapshot.accessToken.isNullOrBlank() &&
            snapshot.expiresAt > System.currentTimeMillis() + 60_000
        ) {
            return true
        }

        val clientId = snapshot.clientId ?: return false
        val refreshToken = snapshot.refreshToken ?: return false

        try {
            val token = authApi.refreshToken(
                RefreshTokenRequest(
                    refreshToken = refreshToken,
                    clientId = clientId,
                    clientSecret = snapshot.clientSecret,
                ),
            )
            persistTokens(token)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            if (error.code() == 400 || error.code() == 401 || error.code() == 403) {
                expireSession("Your Trakt session expired. Please reconnect.")
            }
            false
        } catch (error: IOException) {
            false
        }
    }

    private suspend fun persistTokens(token: TokenResponse) {
        val expiresAt = System.currentTimeMillis() + token.expiresIn * 1000L
        tokenStore.saveTokens(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAt = expiresAt,
        )
        session.update(tokenStore.read())
        val clientId = session.snapshot().clientId ?: return
        _state.value = AuthState.LoggedIn(clientId)
    }

    private suspend fun expireSession(message: String) {
        tokenStore.clearTokens()
        session.clearTokens()
        val clientId = session.snapshot().clientId
        _state.value = if (clientId == null) {
            AuthState.NeedsClientId
        } else {
            AuthState.LoggedOut(clientId, message)
        }
        _deviceLogin.value = DeviceLoginState.Idle
    }

    suspend fun signOut() {
        cancelDeviceLogin()
        val snapshot = session.snapshot()
        if (!snapshot.accessToken.isNullOrBlank() && !snapshot.clientId.isNullOrBlank()) {
            runCatching {
                authApi.revoke(
                    RevokeRequest(
                        token = snapshot.accessToken,
                        clientId = snapshot.clientId,
                        clientSecret = snapshot.clientSecret,
                    ),
                )
            }
        }

        tokenStore.clearTokens()
        session.clearTokens()
        _deviceLogin.value = DeviceLoginState.Idle
        val clientId = session.snapshot().clientId
        _state.value = if (clientId == null) {
            AuthState.NeedsClientId
        } else {
            AuthState.LoggedOut(clientId)
        }
    }

    fun cancelDeviceLogin() {
        pollJob?.cancel()
        pollJob = null
        if (_deviceLogin.value !is DeviceLoginState.Failed) {
            _deviceLogin.value = DeviceLoginState.Idle
        }
    }

    private fun deviceError(error: Exception): String = when (error) {
        is DeviceAuthException -> error.message ?: "Could not connect to Trakt."
        is HttpException -> "Trakt returned HTTP ${error.code()} while connecting."
        is IOException -> "Couldn't reach Trakt. Check your connection."
        else -> "Could not connect to Trakt."
    }

    private class DeviceAuthException(message: String) : Exception(message)
}
