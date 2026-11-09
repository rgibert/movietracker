package ca.gibert.movietracker.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.HistoryEntry
import ca.gibert.movietracker.ui.components.CachedBanner
import ca.gibert.movietracker.ui.components.EmptyState
import ca.gibert.movietracker.ui.components.ErrorState
import ca.gibert.movietracker.ui.components.PosterImage
import ca.gibert.movietracker.ui.components.ScreenHeader
import ca.gibert.movietracker.ui.components.SectionHeader
import ca.gibert.movietracker.ui.components.StarRating
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.util.formatDate
import ca.gibert.movietracker.util.formatMonth
import ca.gibert.movietracker.util.localDate
import ca.gibert.movietracker.util.parseTraktInstant
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    container: AppContainer,
    onOpenMovie: (Int) -> Unit,
    onAddMovie: () -> Unit,
    viewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val diary = state.diary
    val grouped = remember(diary?.entries) {
        diary?.entries.orEmpty()
            .groupBy { entry ->
                parseTraktInstant(entry.watchedAt)?.localDate()?.let(YearMonth::from)
            }
            .toList()
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && diary == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                diary == null -> {
                    ErrorState(
                        message = state.errorMessage ?: "Your diary couldn't be loaded.",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                        title = "Couldn't load your diary",
                    )
                }

                diary.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.AutoStories,
                            title = "Your diary is empty",
                            message = "Log a movie from its page to record when you watched " +
                                "it, with a rating and a review.",
                            action = {
                                Button(onClick = onAddMovie) {
                                    Text("Find a movie")
                                }
                            },
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "header") {
                            ScreenHeader(eyebrow = "Watched", title = "Diary")
                        }

                        if (state.showingCached) {
                            item(key = "cached-banner") {
                                CachedBanner(lastUpdated = diary.fetchedAt)
                            }
                        }

                        grouped.forEach { (month, entries) ->
                            item(key = "month_${month ?: "unknown"}") {
                                SectionHeader(
                                    title = month?.let(::formatMonth) ?: "Date unknown",
                                    trailing = "${entries.size} watched",
                                )
                            }
                            items(items = entries, key = { "play_${it.id}" }) { entry ->
                                DiaryRow(
                                    entry = entry,
                                    rating = entry.movie?.ids?.trakt?.let { diary.ratings[it] },
                                    onClick = { entry.movie?.ids?.trakt?.let(onOpenMovie) },
                                )
                            }
                        }

                        if (diary.hasMore) {
                            item(key = "load-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(Modifier.size(28.dp))
                                    } else {
                                        OutlinedButton(onClick = viewModel::loadMore) {
                                            Text("Show older entries")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun DiaryRow(
    entry: HistoryEntry,
    rating: Int?,
    onClick: () -> Unit,
) {
    val movie = entry.movie ?: return
    val watchedOn = parseTraktInstant(entry.watchedAt)?.localDate()

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                movie = movie,
                modifier = Modifier
                    .width(44.dp)
                    .height(66.dp),
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        movie.year?.toString(),
                        watchedOn?.let { "Watched ${formatDate(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rating != null) {
                    Spacer(Modifier.height(4.dp))
                    StarRating(rating = rating, starSize = 14.dp)
                }
            }
        }
    }
}
