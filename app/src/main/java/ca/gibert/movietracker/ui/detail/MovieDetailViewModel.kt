package ca.gibert.movietracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.MovieDetail
import ca.gibert.movietracker.data.ServiceOverrideStore
import ca.gibert.movietracker.data.TraktRepository
import ca.gibert.movietracker.util.friendlyError
import ca.gibert.movietracker.util.watchedAtFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MovieDetailUiState(
    val detail: MovieDetail? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showingCached: Boolean = false,
    /** The service the user pinned for this movie, if any. */
    val serviceOverride: String? = null,
    val hasProviderData: Boolean = false,
    val region: String = "",
    val isUpdatingWatchlist: Boolean = false,
    val isSavingRating: Boolean = false,
    val isLogging: Boolean = false,
    val deletingIds: Set<Long> = emptySet(),
)

class MovieDetailViewModel(
    private val movieId: Int,
    private val repository: TraktRepository,
    private val serviceOverrides: ServiceOverrideStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MovieDetailUiState(hasProviderData = repository.hasProviderData),
    )
    val state: StateFlow<MovieDetailUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false, userInitiated = true)

    fun toggleWatchlist() {
        val detail = _state.value.detail ?: return
        if (_state.value.isUpdatingWatchlist) return
        val adding = !detail.inWatchlist

        viewModelScope.launch {
            _state.update { it.copy(isUpdatingWatchlist = true) }
            try {
                if (adding) {
                    repository.addToWatchlist(detail.movie)
                } else {
                    repository.removeFromWatchlist(movieId)
                }
                updateDetail { it.copy(inWatchlist = adding) }
                _messages.send(
                    if (adding) "Added to your watchlist" else "Removed from your watchlist",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            } finally {
                _state.update { it.copy(isUpdatingWatchlist = false) }
            }
        }
    }

    fun setRating(rating: Int?) {
        if (_state.value.detail == null || _state.value.isSavingRating) return
        viewModelScope.launch {
            _state.update { it.copy(isSavingRating = true) }
            try {
                repository.setRating(movieId, rating)
                updateDetail { it.copy(rating = rating) }
                _messages.send(if (rating == null) "Rating removed" else "Rating saved")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            } finally {
                _state.update { it.copy(isSavingRating = false) }
            }
        }
    }

    /**
     * Logs a watch — the diary entry — with an optional rating and review.
     * Each part is saved separately so a rejected review doesn't lose the play.
     */
    fun logWatch(date: LocalDate, rating: Int?, review: String, spoiler: Boolean) {
        val detail = _state.value.detail ?: return
        if (_state.value.isLogging) return

        viewModelScope.launch {
            _state.update { it.copy(isLogging = true) }
            try {
                repository.addPlay(movieId, watchedAtFor(date))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
                _state.update { it.copy(isLogging = false) }
                return@launch
            }

            val problems = ArrayList<String>()
            if (rating != null && rating != detail.rating) {
                try {
                    repository.setRating(movieId, rating)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    problems += "rating: ${friendlyError(error)}"
                }
            }
            if (review.isNotBlank()) {
                try {
                    repository.postReview(movieId, review, spoiler)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    problems += "review: ${friendlyError(error)}"
                }
            }

            _messages.send(
                if (problems.isEmpty()) {
                    "Logged ${detail.movie.title}"
                } else {
                    "Logged, but couldn't save the ${problems.joinToString("; ")}"
                },
            )
            _state.update { it.copy(isLogging = false) }
            load(initial = false)
        }
    }

    fun postReview(text: String, spoiler: Boolean) {
        if (_state.value.isLogging) return
        viewModelScope.launch {
            _state.update { it.copy(isLogging = true) }
            try {
                val comment = repository.postReview(movieId, text, spoiler)
                updateDetail { it.copy(comments = listOf(comment) + it.comments) }
                _messages.send("Review posted")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            } finally {
                _state.update { it.copy(isLogging = false) }
            }
        }
    }

    fun deletePlay(historyId: Long) = delete(historyId) {
        repository.removePlay(historyId)
        updateDetail { detail -> detail.copy(history = detail.history.filterNot { it.id == historyId }) }
        "Diary entry removed"
    }

    fun deleteReview(commentId: Long) = delete(commentId) {
        repository.deleteReview(commentId)
        updateDetail { detail -> detail.copy(comments = detail.comments.filterNot { it.id == commentId }) }
        "Review deleted"
    }

    fun setServiceOverride(service: String) {
        val movie = _state.value.detail?.movie ?: return
        viewModelScope.launch {
            try {
                serviceOverrides.set(movie, service)
                val saved = serviceOverrides.readMap()[movieId]
                _state.update { it.copy(serviceOverride = saved) }
                _messages.send(if (saved == null) "Service cleared" else "Watching on $saved")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    fun clearServiceOverride() = setServiceOverride("")

    private fun delete(id: Long, action: suspend () -> String) {
        if (id in _state.value.deletingIds) return
        viewModelScope.launch {
            _state.update { it.copy(deletingIds = it.deletingIds + id) }
            try {
                _messages.send(action())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            } finally {
                _state.update { it.copy(deletingIds = it.deletingIds - id) }
            }
        }
    }

    private suspend fun updateDetail(transform: (MovieDetail) -> MovieDetail) {
        val current = _state.value.detail ?: return
        val updated = transform(current)
        _state.update { it.copy(detail = updated) }
        runCatching { repository.cacheMovieDetail(updated) }
    }

    private fun load(initial: Boolean, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val override = runCatching { serviceOverrides.readMap()[movieId] }.getOrNull()
            val region = runCatching { repository.region() }.getOrDefault("")
            _state.update { it.copy(serviceOverride = override, region = region) }

            if (initial) {
                repository.cachedMovieDetail(movieId)?.let { cached ->
                    _state.update {
                        it.copy(detail = cached, isLoading = false, showingCached = true)
                    }
                }
            }

            _state.update {
                val hasData = it.detail != null
                it.copy(isLoading = !hasData, isRefreshing = hasData, errorMessage = null)
            }

            try {
                val detail = repository.refreshMovieDetail(movieId)
                _state.update {
                    it.copy(
                        detail = detail,
                        isLoading = false,
                        isRefreshing = false,
                        showingCached = false,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = friendlyError(error)
                val hasData = _state.value.detail != null
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (hasData) null else message,
                        showingCached = hasData,
                    )
                }
                if (userInitiated && hasData) _messages.send(message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, movieId: Int): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MovieDetailViewModel(
                        movieId = movieId,
                        repository = container.traktRepository,
                        serviceOverrides = container.serviceOverrideStore,
                    )
                }
            }
    }
}
