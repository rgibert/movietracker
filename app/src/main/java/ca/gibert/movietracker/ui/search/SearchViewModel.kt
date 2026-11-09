package ca.gibert.movietracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.Movie
import ca.gibert.movietracker.data.TraktRepository
import ca.gibert.movietracker.util.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Movie> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    /** Whether a search has completed for the current query. */
    val searched: Boolean = false,
    val watchlistIds: Set<Int> = emptySet(),
    val updatingIds: Set<Int> = emptySet(),
)

class SearchViewModel(
    private val repository: TraktRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val ids = repository.watchlistMovieIds()
                _state.update { it.copy(watchlistIds = ids) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Without it every result simply offers "Add".
            }
        }
    }

    /** Updates the query and searches once typing pauses. */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, searched = false) }
        search(debounce = true)
    }

    fun searchNow() = search(debounce = false)

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        val query = _state.value.query.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _state.update {
                it.copy(results = emptyList(), isSearching = false, errorMessage = null)
            }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            _state.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val results = repository.searchMovies(query)
                _state.update {
                    it.copy(results = results, isSearching = false, searched = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(isSearching = false, errorMessage = friendlyError(error), searched = true)
                }
            }
        }
    }

    fun toggleWatchlist(movie: Movie) {
        val id = movie.ids.trakt
        if (id in _state.value.updatingIds) return
        val adding = id !in _state.value.watchlistIds

        viewModelScope.launch {
            _state.update { it.copy(updatingIds = it.updatingIds + id) }
            try {
                if (adding) {
                    repository.addToWatchlist(movie)
                    _state.update { it.copy(watchlistIds = it.watchlistIds + id) }
                    _messages.send("Added ${movie.title} to your watchlist")
                } else {
                    repository.removeFromWatchlist(id)
                    _state.update { it.copy(watchlistIds = it.watchlistIds - id) }
                    _messages.send("Removed ${movie.title} from your watchlist")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            } finally {
                _state.update { it.copy(updatingIds = it.updatingIds - id) }
            }
        }
    }

    /** Re-reads watchlist membership after visiting a movie page. */
    fun onResume() {
        viewModelScope.launch {
            try {
                val ids = repository.watchlistMovieIds()
                _state.update { it.copy(watchlistIds = ids) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep what we have.
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
        private const val MIN_QUERY_LENGTH = 2

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(repository = container.traktRepository) }
        }
    }
}
