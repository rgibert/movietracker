package ca.gibert.movietracker.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.CachedDiary
import ca.gibert.movietracker.data.TraktRepository
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

data class DiaryUiState(
    val diary: CachedDiary? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val showingCached: Boolean = false,
)

class DiaryViewModel(
    private val repository: TraktRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiaryUiState())
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var appliedMutationVersion = 0L

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false, userInitiated = true)

    fun onResume() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        val hasLocalMutation = repository.mutationVersion != appliedMutationVersion
        val fetchedAt = _state.value.diary?.fetchedAt
        val fresh = fetchedAt != null &&
            System.currentTimeMillis() - fetchedAt < AUTO_REFRESH_INTERVAL_MS
        if (!hasLocalMutation && fresh) return
        load(initial = false)
    }

    fun loadMore() {
        val current = _state.value.diary ?: return
        if (!current.hasMore || _state.value.isLoadingMore) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val updated = repository.loadMoreDiary(current)
                _state.update { it.copy(diary = updated) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            } finally {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun load(initial: Boolean, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val mutationVersionAtStart = repository.mutationVersion

            if (initial) {
                repository.cachedDiary()?.let { cached ->
                    _state.update {
                        it.copy(diary = cached, isLoading = false, showingCached = true)
                    }
                }
            }

            _state.update {
                val hasData = it.diary != null
                it.copy(isLoading = !hasData, isRefreshing = hasData, errorMessage = null)
            }

            try {
                val snapshot = repository.refreshDiary()
                appliedMutationVersion = mutationVersionAtStart
                _state.update {
                    it.copy(
                        diary = snapshot,
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
                val hasData = _state.value.diary != null
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
        private const val AUTO_REFRESH_INTERVAL_MS = 15 * 60 * 1000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiaryViewModel(repository = container.traktRepository) }
        }
    }
}
