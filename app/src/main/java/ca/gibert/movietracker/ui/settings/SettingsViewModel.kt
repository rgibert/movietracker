package ca.gibert.movietracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.AuthRepository
import ca.gibert.movietracker.data.AuthState
import ca.gibert.movietracker.data.ServiceOverride
import ca.gibert.movietracker.data.ServiceOverrideStore
import ca.gibert.movietracker.data.TraktRepository
import ca.gibert.movietracker.data.User
import ca.gibert.movietracker.util.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: User? = null,
    val clientId: String = "",
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val isBusy: Boolean = false,
    val region: String = "",
    val serviceOverrides: List<ServiceOverride> = emptyList(),
    val hasProviderData: Boolean = false,
)

class SettingsViewModel(
    private val container: AppContainer,
    private val repository: TraktRepository,
    private val authRepository: AuthRepository,
    private val serviceOverrideStore: ServiceOverrideStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            clientId = (authRepository.state.value as? AuthState.LoggedIn)?.clientId.orEmpty(),
            hasProviderData = repository.hasProviderData,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        loadProfile()
        loadLocal()
    }

    fun refreshProfile() = loadProfile()

    /** Saves the release/streaming country; blank goes back to the device's. */
    fun setRegion(region: String) {
        viewModelScope.launch {
            try {
                repository.setRegion(region)
                val saved = repository.region()
                _state.update { it.copy(region = saved) }
                _messages.send("Release dates and services now use $saved")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    /** Drops a pinned service so the movie goes back to TMDB's providers. */
    fun resetService(override: ServiceOverride) {
        viewModelScope.launch {
            try {
                serviceOverrideStore.remove(override.trakt)
                _state.update { state ->
                    state.copy(
                        serviceOverrides = state.serviceOverrides.filterNot { it.trakt == override.trakt },
                    )
                }
                _messages.send("${override.title} no longer has a pinned service")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    private fun loadLocal() {
        viewModelScope.launch {
            try {
                val region = repository.region()
                val overrides = serviceOverrideStore.read()
                _state.update { it.copy(region = region, serviceOverrides = overrides) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Local and cheap to re-read; leave the sections at defaults.
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                container.signOut()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun forgetApiApp() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                container.forgetApiApp()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                repository.clearCache()
                _messages.send("Saved data cleared")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingProfile = true, profileError = null) }
            try {
                val profile = repository.getProfile()
                _state.update {
                    it.copy(profile = profile, isLoadingProfile = false, profileError = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoadingProfile = false, profileError = friendlyError(error))
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container = container,
                    repository = container.traktRepository,
                    authRepository = container.authRepository,
                    serviceOverrideStore = container.serviceOverrideStore,
                )
            }
        }
    }
}
