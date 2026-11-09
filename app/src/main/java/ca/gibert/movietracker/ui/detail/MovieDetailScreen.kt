package ca.gibert.movietracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.Comment
import ca.gibert.movietracker.data.HistoryEntry
import ca.gibert.movietracker.data.MovieDetail
import ca.gibert.movietracker.data.ProviderType
import ca.gibert.movietracker.data.StreamingProvider
import ca.gibert.movietracker.data.releaseEvents
import ca.gibert.movietracker.ui.components.CachedBanner
import ca.gibert.movietracker.ui.components.ErrorState
import ca.gibert.movietracker.ui.components.MovieBackdrop
import ca.gibert.movietracker.ui.components.Pill
import ca.gibert.movietracker.ui.components.PosterImage
import ca.gibert.movietracker.ui.components.ServiceBadge
import ca.gibert.movietracker.ui.components.StarColor
import ca.gibert.movietracker.ui.components.StarRating
import ca.gibert.movietracker.ui.components.launchIntentFor
import ca.gibert.movietracker.ui.components.streamingAppFor
import ca.gibert.movietracker.ui.components.suggestedServices
import ca.gibert.movietracker.ui.openUrl
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.ui.watch.movieMeta
import ca.gibert.movietracker.util.MIN_REVIEW_WORDS
import ca.gibert.movietracker.util.REVIEW_WORDS
import ca.gibert.movietracker.util.formatDate
import ca.gibert.movietracker.util.localDate
import ca.gibert.movietracker.util.parseTraktInstant
import ca.gibert.movietracker.util.wordCount
import java.time.LocalDate
import java.util.Locale

private enum class DetailDialog { None, Log, Review, Service }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    container: AppContainer,
    movieId: Int,
    viewModel: MovieDetailViewModel = viewModel(
        key = "movie_$movieId",
        factory = MovieDetailViewModel.factory(container, movieId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf(DetailDialog.None) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val detail = state.detail
            when {
                state.isLoading && detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                detail == null -> {
                    ErrorState(
                        message = state.errorMessage ?: "This movie couldn't be loaded.",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                        title = "Couldn't load this movie",
                    )
                }

                else -> {
                    DetailContent(
                        detail = detail,
                        state = state,
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        onRate = viewModel::setRating,
                        onLog = { dialog = DetailDialog.Log },
                        onReview = { dialog = DetailDialog.Review },
                        onEditService = { dialog = DetailDialog.Service },
                        onDeletePlay = viewModel::deletePlay,
                        onDeleteReview = viewModel::deleteReview,
                    )
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

    val detail = state.detail
    when {
        detail == null -> Unit
        dialog == DetailDialog.Log -> LogWatchDialog(
            title = detail.movie.title,
            initialRating = detail.rating,
            onLog = { date, rating, review, spoiler ->
                dialog = DetailDialog.None
                viewModel.logWatch(date, rating, review, spoiler)
            },
            onDismiss = { dialog = DetailDialog.None },
        )

        dialog == DetailDialog.Review -> ReviewDialog(
            title = detail.movie.title,
            onPost = { text, spoiler ->
                dialog = DetailDialog.None
                viewModel.postReview(text, spoiler)
            },
            onDismiss = { dialog = DetailDialog.None },
        )

        dialog == DetailDialog.Service -> ServiceDialog(
            current = state.serviceOverride,
            providers = detail.providers,
            onSave = { name ->
                dialog = DetailDialog.None
                viewModel.setServiceOverride(name)
            },
            onReset = {
                dialog = DetailDialog.None
                viewModel.clearServiceOverride()
            },
            onDismiss = { dialog = DetailDialog.None },
        )
    }
}

@Composable
private fun DetailContent(
    detail: MovieDetail,
    state: MovieDetailUiState,
    onToggleWatchlist: () -> Unit,
    onRate: (Int?) -> Unit,
    onLog: () -> Unit,
    onReview: () -> Unit,
    onEditService: () -> Unit,
    onDeletePlay: (Long) -> Unit,
    onDeleteReview: (Long) -> Unit,
) {
    val sectionPadding = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "hero") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (state.showingCached) {
                    CachedBanner(lastUpdated = detail.fetchedAt)
                    Spacer(Modifier.height(12.dp))
                }
                MovieHero(detail)
            }
        }

        item(key = "actions") {
            ActionsRow(
                inWatchlist = detail.inWatchlist,
                isUpdatingWatchlist = state.isUpdatingWatchlist,
                isLogging = state.isLogging,
                onToggleWatchlist = onToggleWatchlist,
                onLog = onLog,
                modifier = sectionPadding,
            )
        }

        item(key = "rating") {
            RatingCard(
                rating = detail.rating,
                isSaving = state.isSavingRating,
                onRate = onRate,
                modifier = sectionPadding,
            )
        }

        item(key = "where") {
            WhereToWatchCard(
                detail = detail,
                serviceOverride = state.serviceOverride,
                hasProviderData = state.hasProviderData,
                region = state.region,
                onEditService = onEditService,
                modifier = sectionPadding,
            )
        }

        item(key = "releases") {
            ReleasesCard(detail = detail, region = state.region, modifier = sectionPadding)
        }

        item(key = "diary") {
            DiaryCard(
                history = detail.history,
                deletingIds = state.deletingIds,
                onLog = onLog,
                onDelete = onDeletePlay,
                modifier = sectionPadding,
            )
        }

        item(key = "reviews") {
            ReviewsCard(
                comments = detail.comments,
                deletingIds = state.deletingIds,
                isPosting = state.isLogging,
                onReview = onReview,
                onDelete = onDeleteReview,
                modifier = sectionPadding,
            )
        }

        item(key = "trakt-link") {
            val context = LocalContext.current
            val slug = detail.movie.ids.slug ?: detail.movie.ids.trakt.toString()
            TextButton(
                onClick = { context.openUrl("https://trakt.tv/movies/$slug") },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("View on Trakt")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieHero(detail: MovieDetail) {
    val movie = detail.movie
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column {
            Box {
                MovieBackdrop(
                    movie = movie,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-36).dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                PosterImage(
                    movie = movie,
                    modifier = Modifier
                        .width(92.dp)
                        .height(138.dp),
                    cornerRadius = 14.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    movieMeta(movie)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    movie.rating?.takeIf { it > 0 }?.let { rating ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "★ ${String.format(Locale.getDefault(), "%.1f", rating)} on Trakt" +
                                (movie.votes?.let { " · $it votes" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = StarColor,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-24).dp),
            ) {
                movie.tagline?.takeIf { it.isNotBlank() }?.let { tagline ->
                    Text(
                        text = tagline,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                movie.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(text = overview, style = MaterialTheme.typography.bodyMedium)
                }
                if (movie.genres.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        movie.genres.forEach { genre ->
                            Pill(genre.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsRow(
    inWatchlist: Boolean,
    isUpdatingWatchlist: Boolean,
    isLogging: Boolean,
    onToggleWatchlist: () -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onToggleWatchlist,
            enabled = !isUpdatingWatchlist,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (inWatchlist) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (inWatchlist) "On watchlist" else "Add to watchlist", maxLines = 1)
        }
        Button(
            onClick = onLog,
            enabled = !isLogging,
            modifier = Modifier.weight(1f),
        ) {
            if (isLogging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Log a watch", maxLines = 1)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun RatingCard(
    rating: Int?,
    isSaving: Boolean,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Your rating",
        modifier = modifier,
        trailing = {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (rating != null) {
                TextButton(onClick = { onRate(null) }) { Text("Clear") }
            }
        },
    ) {
        StarRating(
            rating = rating,
            starSize = 36.dp,
            onRate = if (isSaving) null else onRate,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (rating == null) {
                "Tap a star; tap its left half for a half star."
            } else {
                "${rating}/10 on Trakt"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhereToWatchCard(
    detail: MovieDetail,
    serviceOverride: String?,
    hasProviderData: Boolean,
    region: String,
    onEditService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val groups = listOf(
        "Stream" to detail.providers.filter { it.type == ProviderType.STREAM },
        "Free" to detail.providers.filter { it.type == ProviderType.FREE },
        "Rent" to detail.providers.filter { it.type == ProviderType.RENT },
        "Buy" to detail.providers.filter { it.type == ProviderType.BUY },
    ).filter { it.second.isNotEmpty() }

    SectionCard(
        title = if (region.isNotBlank()) "Where to watch · $region" else "Where to watch",
        modifier = modifier,
    ) {
        PinnedServiceRow(
            serviceOverride = serviceOverride,
            onClick = onEditService,
        )

        val openTarget = serviceOverride ?: detail.providers.firstOrNull { it.type != ProviderType.BUY }?.name
        OpenInAppRow(service = openTarget)

        when {
            groups.isNotEmpty() -> {
                groups.forEach { (label, providers) ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        providers.forEach { ProviderChip(it) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Data from JustWatch via TMDB",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    detail.providerLink?.let { link ->
                        TextButton(onClick = { context.openUrl(link) }) { Text("All options") }
                    }
                }
            }

            hasProviderData -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No streaming, rental or purchase options are listed in $region yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            else -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This build has no TMDB key, so streaming services aren't looked up. " +
                        "Pin where you'll watch it above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderChip(provider: StreamingProvider) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServiceBadge(network = provider.name, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(text = provider.name, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PinnedServiceRow(
    serviceOverride: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServiceBadge(network = serviceOverride.orEmpty(), size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = serviceOverride?.let { "Watching on $it" } ?: "Pin where you'll watch it",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (serviceOverride != null) {
                    "Shown on To Watch and Coming Up"
                } else {
                    "For a service or server not listed here"
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Edit, contentDescription = "Change service", tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpenInAppRow(service: String?) {
    val context = LocalContext.current
    val app = remember(service) { streamingAppFor(service) } ?: return
    val launchIntent = remember(app) { launchIntentFor(context, app) } ?: return

    TextButton(
        onClick = {
            // The app can be uninstalled between composition and the tap.
            runCatching { context.startActivity(launchIntent) }
        },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Open ${app.name}")
    }
}

@Composable
private fun ReleasesCard(
    detail: MovieDetail,
    region: String,
    modifier: Modifier = Modifier,
) {
    val events = remember(detail) { releaseEvents(detail.movie, detail.releases) }
    val today = remember { LocalDate.now() }

    SectionCard(
        title = if (region.isNotBlank()) "Release dates · $region" else "Release dates",
        modifier = modifier,
    ) {
        if (events.isEmpty()) {
            Text(
                text = "Trakt has no release dates for this movie yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            events.forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = event.kind.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDate(event.date, today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (event.date.isAfter(today)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiaryCard(
    history: List<HistoryEntry>,
    deletingIds: Set<Long>,
    onLog: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    SectionCard(
        title = "Your diary",
        modifier = modifier,
        trailing = { TextButton(onClick = onLog) { Text("Log") } },
    ) {
        if (history.isEmpty()) {
            Text(
                text = "You haven't logged this movie yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            history.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                val date = parseTraktInstant(entry.watchedAt)?.localDate()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = date?.let { "Watched ${formatDate(it)}" } ?: "Watched",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    // Newest first, so every entry but the last is a rewatch.
                    if (index < history.size - 1) {
                        Pill("Rewatch")
                    }
                    if (entry.id in deletingIds) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { confirmDelete = entry.id }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove diary entry",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        ConfirmDialog(
            title = "Remove this diary entry?",
            message = "The watch is removed from your Trakt history.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmDelete = null
                onDelete(id)
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun ReviewsCard(
    comments: List<Comment>,
    deletingIds: Set<Long>,
    isPosting: Boolean,
    onReview: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var revealedSpoilers by remember { mutableStateOf(emptySet<Long>()) }

    SectionCard(
        title = "Your reviews",
        modifier = modifier,
        trailing = {
            TextButton(onClick = onReview, enabled = !isPosting) {
                Icon(Icons.Outlined.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Write")
            }
        },
    ) {
        if (comments.isEmpty()) {
            Text(
                text = "Reviews you post on Trakt for this movie appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            comments.forEachIndexed { index, comment ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val posted = parseTraktInstant(comment.createdAt)?.localDate()
                            Text(
                                text = posted?.let { formatDate(it) } ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (comment.review) {
                                Spacer(Modifier.width(6.dp))
                                Pill("Review")
                            }
                            comment.userRating?.let {
                                Spacer(Modifier.width(6.dp))
                                StarRating(rating = it, starSize = 12.dp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val hidden = comment.spoiler && comment.id !in revealedSpoilers
                        if (hidden) {
                            TextButton(onClick = { revealedSpoilers = revealedSpoilers + comment.id }) {
                                Text("Contains spoilers · tap to show")
                            }
                        } else {
                            Text(text = comment.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (comment.id in deletingIds) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { confirmDelete = comment.id }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete review",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        ConfirmDialog(
            title = "Delete this review?",
            message = "It's deleted from Trakt. Trakt only allows deleting within two weeks of posting.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = null
                onDelete(id)
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceDialog(
    current: String?,
    providers: List<StreamingProvider>,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val suggestions = remember(providers) {
        (providers.filter { it.type != ProviderType.BUY }.map { it.name } + suggestedServices).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where will you watch it?") },
        text = {
            Column {
                Text(
                    text = "Pin a service for this movie; it's shown on To Watch and Coming Up " +
                        "and counts the movie as available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Service name") },
                    singleLine = true,
                    leadingIcon = { ServiceBadge(network = name.trim(), size = 22.dp) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // Bounded and scrollable so the field and buttons stay on
                // screen on short displays or with large font sizes.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    suggestions.forEach { service ->
                        SuggestionChip(
                            onClick = { name = service },
                            label = { Text(service) },
                            icon = { ServiceBadge(network = service, size = 16.dp) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = onReset) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Shared review field with a spoiler toggle and Trakt's five-word minimum. */
@Composable
internal fun ReviewFields(
    text: String,
    onTextChange: (String) -> Unit,
    spoiler: Boolean,
    onSpoilerChange: (Boolean) -> Unit,
    optional: Boolean,
) {
    val words = wordCount(text)
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(if (optional) "Review (optional)" else "Review") },
        minLines = 3,
        maxLines = 8,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        supportingText = {
            Text(
                when {
                    text.isBlank() && optional -> "Shared publicly on Trakt"
                    words < MIN_REVIEW_WORDS -> "At least $MIN_REVIEW_WORDS words · $words so far"
                    words > REVIEW_WORDS -> "$words words · posted as a full review"
                    else -> "$words words"
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = spoiler, onCheckedChange = onSpoilerChange)
        Text("Contains spoilers", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewDialog(
    title: String,
    onPost: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    val valid = wordCount(text) >= MIN_REVIEW_WORDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review $title") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReviewFields(
                    text = text,
                    onTextChange = { text = it },
                    spoiler = spoiler,
                    onSpoilerChange = { spoiler = it },
                    optional = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPost(text, spoiler) }, enabled = valid) {
                Text("Post", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
