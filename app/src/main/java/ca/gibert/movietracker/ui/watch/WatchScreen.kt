package ca.gibert.movietracker.ui.watch

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.Availability
import ca.gibert.movietracker.data.Movie
import ca.gibert.movietracker.data.ProviderType
import ca.gibert.movietracker.data.ToWatchItem
import ca.gibert.movietracker.data.WatchlistSort
import ca.gibert.movietracker.data.buildToWatch
import ca.gibert.movietracker.data.sortedFor
import ca.gibert.movietracker.ui.components.CachedBanner
import ca.gibert.movietracker.ui.components.EmptyState
import ca.gibert.movietracker.ui.components.ErrorState
import ca.gibert.movietracker.ui.components.LastUpdatedFooter
import ca.gibert.movietracker.ui.components.Pill
import ca.gibert.movietracker.ui.components.PosterImage
import ca.gibert.movietracker.ui.components.ScreenHeader
import ca.gibert.movietracker.ui.components.ServiceLine
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.ui.theme.ZenIconButton
import ca.gibert.movietracker.util.formatDate
import ca.gibert.movietracker.util.formatRuntime
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    container: AppContainer,
    onOpenMovie: (Int) -> Unit,
    onAddMovie: () -> Unit,
    viewModel: WatchlistViewModel = viewModel(factory = WatchlistViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WatchlistEvent.Removed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Removed ${event.movie.title} from your watchlist",
                        actionLabel = "Undo",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemove(event.movie)
                    }
                }

                is WatchlistEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val today = remember { LocalDate.now() }
    val items = remember(state.items, state.serviceOverrides, state.removedIds, state.sort, today) {
        buildToWatch(
            items = state.items.filter { it.movie.ids.trakt !in state.removedIds },
            overrides = state.serviceOverrides,
            today = today,
        ).sortedFor(state.sort)
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                items.isEmpty() && state.errorMessage != null -> {
                    ErrorState(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Movie,
                            title = "Nothing to watch yet",
                            message = "Movies on your Trakt watchlist that are out now will " +
                                "appear here. Ones still to come are under Coming Up.",
                            action = {
                                Button(onClick = onAddMovie) {
                                    Text("Add a movie")
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "header") {
                            ScreenHeader(eyebrow = "Your watchlist", title = "To Watch") {
                                SortButton(selected = state.sort, onSelect = viewModel::setSort)
                            }
                        }

                        if (state.showingCached) {
                            item(key = "cached-banner") {
                                CachedBanner(lastUpdated = state.lastUpdated)
                            }
                        }

                        items(
                            items = items,
                            key = { item -> "to_watch_${item.movie.ids.trakt}" },
                        ) { item ->
                            ToWatchCard(
                                item = item,
                                isRemoving = item.movie.ids.trakt in state.removingIds,
                                onClick = { onOpenMovie(item.movie.ids.trakt) },
                                onRemove = { viewModel.remove(item.movie) },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        if (!state.hasProviderData) {
                            item(key = "no-provider-data") {
                                Text(
                                    text = "Streaming services aren't available in this build. " +
                                        "Pin where you'll watch a movie from its page.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }

                        item(key = "footer") {
                            LastUpdatedFooter(state.lastUpdated)
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
private fun ToWatchCard(
    item: ToWatchItem,
    isRemoving: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movie = item.movie
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterImage(
                movie = movie,
                modifier = Modifier
                    .width(64.dp)
                    .height(96.dp),
                cornerRadius = 14.dp,
            )

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                movieMeta(movie)?.let { meta ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(8.dp))
                AvailabilityLine(item)
            }

            Spacer(Modifier.width(8.dp))

            ZenIconButton(
                onClick = onRemove,
                enabled = !isRemoving,
            ) {
                if (isRemoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onSurface,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkRemove,
                        contentDescription = "Remove ${movie.title} from watchlist",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortButton(
    selected: WatchlistSort,
    onSelect: (WatchlistSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box {
        GlassSurface(
            shape = CircleShape,
            onClick = { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            WatchlistSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun AvailabilityLine(item: ToWatchItem) {
    val colors = MaterialTheme.colorScheme
    val providers = item.entry.providers

    when (item.availability) {
        Availability.THEATERS -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("In theatres", emphasis = true)
                item.nextHome?.let { next ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${next.kind.label} ${formatDate(next.date)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.service != null) {
                Spacer(Modifier.height(6.dp))
                ServiceLine(network = item.service)
            }
        }

        else -> {
            val service = item.service
            if (service != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServiceLine(network = service, modifier = Modifier.weight(1f, fill = false))
                    val more = providers.count { it.type.isIncluded() && it.name != service }
                    if (more > 0) {
                        Spacer(Modifier.width(6.dp))
                        Pill("+$more")
                    }
                }
            } else {
                val stores = providers.filter { !it.type.isIncluded() }.map { it.name }.distinct()
                Text(
                    text = if (stores.isNotEmpty()) {
                        "Rent or buy · ${stores.take(3).joinToString(", ")}"
                    } else {
                        "Out now"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun ProviderType.isIncluded(): Boolean =
    this == ProviderType.STREAM || this == ProviderType.FREE

internal fun movieMeta(movie: Movie): String? = listOfNotNull(
    movie.year?.toString(),
    formatRuntime(movie.runtime),
    movie.certification?.takeIf { it.isNotBlank() },
).joinToString(" · ").ifBlank { null }
