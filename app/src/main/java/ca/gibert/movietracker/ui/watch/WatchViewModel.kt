package ca.gibert.movietracker.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.Movie
import ca.gibert.movietracker.data.ServiceOverrideStore
import ca.gibert.movietracker.data.TraktRepository
import ca.gibert.movietracker.data.WatchlistMovie
import ca.gibert.movietracker.data.WatchlistSort
import ca.gibert.movietracker.data.WatchlistSortStore
import ca.gibert.movietracker.util.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State shared by To Watch and Coming Up: both are views of the same watchlist
 * snapshot, so each screen keeps one of these and filters it differently.
 */
data class WatchlistUiState(
    val items: List<WatchlistMovie> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showingCached: Boolean = false,
    val lastUpdated: Long? = null,
    /** Movie id → service the user pinned. */
    val serviceOverrides: Map<Int, String> = emptyMap(),
    val removingIds: Set<Int> = emptySet(),
    /** Movies removed this session, hidden until the next refresh confirms it. */
    val removedIds: Set<Int> = emptySet(),
    val hasProviderData: Boolean = false,
    val region: String = "",
    /** Order of the To Watch list. */
    val sort: WatchlistSort = WatchlistSort.DEFAULT,
)

sealed interface WatchlistEvent {
    data class Removed(val movie: Movie) : WatchlistEvent
    data class Message(val text: String) : WatchlistEvent
}

class WatchlistViewModel(
    private val repository: TraktRepository,
    private val serviceOverrides: ServiceOverrideStore,
    private val sortStore: WatchlistSortStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WatchlistUiState(hasProviderData = repository.hasProviderData),
    )
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    private val _events = Channel<WatchlistEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var appliedMutationVersion = 0L

    init {
        viewModelScope.launch {
            val sort = sortStore.read()
            _state.update { it.copy(sort = sort) }
        }
        load(initial = true)
    }

    fun setSort(sort: WatchlistSort) {
        _state.update { it.copy(sort = sort) }
        viewModelScope.launch { sortStore.set(sort) }
    }

    fun refresh() = load(initial = false, userInitiated = true)

    /**
     * Called when the screen becomes visible again, for example after a movie
     * page where it may have been logged, removed or given a pinned service.
     */
    fun onResume() {
        viewModelScope.launch { syncLocal() }
        if (_state.value.isLoading || _state.value.isRefreshing) return

        val hasLocalMutation = repository.mutationVersion != appliedMutationVersion
        if (!hasLocalMutation && isFresh(_state.value.lastUpdated)) return

        load(initial = false)
    }

    fun remove(movie: Movie) {
        val movieId = movie.ids.trakt
        if (movieId in _state.value.removingIds) return

        viewModelScope.launch {
            _state.update { it.copy(removingIds = it.removingIds + movieId) }
            try {
                repository.removeFromWatchlist(movieId)
                _state.update { it.copy(removedIds = it.removedIds + movieId) }
                _events.send(WatchlistEvent.Removed(movie))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(WatchlistEvent.Message(friendlyError(error)))
            } finally {
                _state.update { it.copy(removingIds = it.removingIds - movieId) }
            }
        }
    }

    fun undoRemove(movie: Movie) {
        viewModelScope.launch {
            try {
                repository.addToWatchlist(movie)
                _state.update { it.copy(removedIds = it.removedIds - movie.ids.trakt) }
                _events.send(WatchlistEvent.Message("${movie.title} is back on your watchlist"))
                load(initial = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(WatchlistEvent.Message(friendlyError(error)))
            }
        }
    }

    private suspend fun syncLocal() {
        val overrides = serviceOverrides.readMap()
        _state.update { it.copy(serviceOverrides = overrides) }
    }

    private fun load(initial: Boolean, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            syncLocal()
            val mutationVersionAtStart = repository.mutationVersion

            if (initial) {
                val cached = repository.cachedWatchlist()
                if (cached != null) {
                    _state.update {
                        it.copy(
                            items = cached.items,
                            region = cached.region,
                            lastUpdated = cached.fetchedAt,
                            isLoading = false,
                            showingCached = true,
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    isLoading = it.items.isEmpty(),
                    isRefreshing = it.items.isNotEmpty(),
                    errorMessage = null,
                )
            }

            try {
                val snapshot = repository.refreshWatchlist()
                appliedMutationVersion = mutationVersionAtStart
                _state.update {
                    it.copy(
                        items = snapshot.items,
                        region = snapshot.region,
                        lastUpdated = snapshot.fetchedAt,
                        isLoading = false,
                        isRefreshing = false,
                        showingCached = false,
                        errorMessage = null,
                        removedIds = emptySet(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = friendlyError(error)
                val hasItems = _state.value.items.isNotEmpty()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (hasItems) null else message,
                        showingCached = hasItems,
                    )
                }
                if (userInitiated && hasItems) {
                    _events.send(WatchlistEvent.Message(message))
                }
            }
        }
    }

    private fun isFresh(lastUpdated: Long?): Boolean =
        lastUpdated != null &&
            System.currentTimeMillis() - lastUpdated < AUTO_REFRESH_INTERVAL_MS

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 15 * 60 * 1000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WatchlistViewModel(
                    repository = container.traktRepository,
                    serviceOverrides = container.serviceOverrideStore,
                    sortStore = container.watchlistSortStore,
                )
            }
        }
    }
}
