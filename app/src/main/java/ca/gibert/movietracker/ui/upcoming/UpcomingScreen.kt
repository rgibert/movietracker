package ca.gibert.movietracker.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.ComingUpItem
import ca.gibert.movietracker.data.ReleaseKind
import ca.gibert.movietracker.data.WatchlistMovie
import ca.gibert.movietracker.data.buildComingUp
import ca.gibert.movietracker.data.featuredService
import ca.gibert.movietracker.ui.components.CachedBanner
import ca.gibert.movietracker.ui.components.EmptyState
import ca.gibert.movietracker.ui.components.ErrorState
import ca.gibert.movietracker.ui.components.LastUpdatedFooter
import ca.gibert.movietracker.ui.components.Pill
import ca.gibert.movietracker.ui.components.PosterImage
import ca.gibert.movietracker.ui.components.ScreenHeader
import ca.gibert.movietracker.ui.components.ServiceLine
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.ui.theme.LocalZenColors
import ca.gibert.movietracker.ui.watch.WatchlistEvent
import ca.gibert.movietracker.ui.watch.WatchlistViewModel
import ca.gibert.movietracker.util.formatDate
import ca.gibert.movietracker.util.formatMonth
import ca.gibert.movietracker.util.formatRelativeDay
import ca.gibert.movietracker.util.monthCalendarDays
import ca.gibert.movietracker.util.relativeDaysLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private enum class UpcomingViewMode(val label: String) {
    List("List"),
    Calendar("Calendar"),
}

private const val CALENDAR_MONTHS_AHEAD = 24L
private const val MONTH_SWIPE_THRESHOLD = 80f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
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
                is WatchlistEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is WatchlistEvent.Removed -> Unit
            }
        }
    }

    val today = remember { LocalDate.now() }
    val comingUp = remember(state.items, state.serviceOverrides, today) {
        buildComingUp(state.items, state.serviceOverrides, today)
    }
    // The list shows each movie once, under its next release; the calendar marks
    // every day a movie has a release on.
    val listGroups = remember(comingUp) { comingUp.byNextRelease() }
    val grouped = remember(comingUp) { comingUp.byReleaseDate() }
    val isEmpty = comingUp.dated.isEmpty() && comingUp.undated.isEmpty()

    var viewMode by rememberSaveable { mutableStateOf(UpcomingViewMode.List) }
    val currentMonth = remember(today) { YearMonth.from(today) }
    val monthLimit = remember(currentMonth) { currentMonth.plusMonths(CALENDAR_MONTHS_AHEAD) }

    // A negative sentinel means "no explicit selection yet", so the default can
    // follow the data when the first refresh arrives.
    var selectedEpochDay by rememberSaveable { mutableStateOf(-1L) }
    var shownMonthText by rememberSaveable { mutableStateOf("") }
    val defaultSelectedDate = remember(grouped, today) { grouped.keys.firstOrNull() ?: today }
    val selectedDate = if (selectedEpochDay >= 0) {
        LocalDate.ofEpochDay(selectedEpochDay)
    } else {
        defaultSelectedDate
    }
    val shownMonth = shownMonthText
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?.takeIf { !it.isBefore(currentMonth) && !it.isAfter(monthLimit) }
        ?: YearMonth.from(selectedDate).coerceIn(currentMonth, monthLimit)

    val releaseCounts = remember(grouped, shownMonth) {
        grouped.filterKeys { YearMonth.from(it) == shownMonth }.mapValues { it.value.size }
    }
    val selectedEntries = grouped[selectedDate].orEmpty()

    fun selectDate(date: LocalDate) {
        selectedEpochDay = date.toEpochDay()
        shownMonthText = YearMonth.from(date).toString()
    }

    fun shiftMonth(offset: Long) {
        val target = shownMonth.plusMonths(offset)
        if (target < currentMonth || target > monthLimit) return
        shownMonthText = target.toString()
        val firstRelease = grouped.keys.firstOrNull { YearMonth.from(it) == target }
        selectedEpochDay = when {
            firstRelease != null -> firstRelease.toEpochDay()
            target == currentMonth -> today.toEpochDay()
            else -> target.atDay(1).toEpochDay()
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && isEmpty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                isEmpty && state.errorMessage != null -> {
                    ErrorState(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                isEmpty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "Nothing coming up",
                            message = "Watchlist movies that aren't out yet appear here with " +
                                "their theatrical, digital and disc release dates.",
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "header") {
                            ScreenHeader(
                                eyebrow = if (state.region.isNotBlank()) {
                                    "Release dates · ${state.region}"
                                } else {
                                    "Release dates"
                                },
                                title = "Coming Up",
                            ) {
                                ViewModeToggle(selected = viewMode, onSelect = { viewMode = it })
                            }
                        }

                        if (state.showingCached) {
                            item(key = "cached-banner") {
                                CachedBanner(lastUpdated = state.lastUpdated)
                            }
                        }

                        when (viewMode) {
                            UpcomingViewMode.List -> {
                                listGroups.forEach { (date, entries) ->
                                    item(key = "day_$date") {
                                        DayHeader(date = date, count = entries.size)
                                    }
                                    items(
                                        items = entries,
                                        key = { "release_${it.movie.ids.trakt}_$date" },
                                    ) { item ->
                                        ReleaseRow(
                                            item = item,
                                            day = date,
                                            service = featuredService(
                                                item.movie.ids.trakt,
                                                item.entry.providers,
                                                state.serviceOverrides,
                                            ),
                                            onClick = { onOpenMovie(item.movie.ids.trakt) },
                                        )
                                    }
                                }

                                if (comingUp.undated.isNotEmpty()) {
                                    item(key = "undated-header") {
                                        UndatedHeader(count = comingUp.undated.size)
                                    }
                                    items(
                                        items = comingUp.undated,
                                        key = { "undated_${it.movie.ids.trakt}" },
                                    ) { entry ->
                                        UndatedRow(
                                            entry = entry,
                                            onClick = { onOpenMovie(entry.movie.ids.trakt) },
                                        )
                                    }
                                }
                            }

                            UpcomingViewMode.Calendar -> {
                                item(key = "calendar-grid") {
                                    UpcomingCalendarCard(
                                        month = shownMonth,
                                        selectedDate = selectedDate,
                                        today = today,
                                        releaseCounts = releaseCounts,
                                        canGoPrevious = shownMonth > currentMonth,
                                        canGoNext = shownMonth < monthLimit,
                                        onPrevious = { shiftMonth(-1) },
                                        onNext = { shiftMonth(1) },
                                        onSelectDate = ::selectDate,
                                    )
                                }

                                item(key = "selected-day-$selectedDate") {
                                    DayHeader(date = selectedDate, count = selectedEntries.size)
                                }

                                if (selectedEntries.isEmpty()) {
                                    item(key = "selected-day-empty") {
                                        CalendarEmptyDay(
                                            message = if (releaseCounts.isEmpty()) {
                                                "Nothing from your watchlist comes out this month."
                                            } else {
                                                "Nothing comes out on this day."
                                            },
                                        )
                                    }
                                } else {
                                    items(
                                        items = selectedEntries,
                                        key = { "selected_${it.movie.ids.trakt}" },
                                    ) { item ->
                                        ReleaseRow(
                                            item = item,
                                            day = selectedDate,
                                            service = featuredService(
                                                item.movie.ids.trakt,
                                                item.entry.providers,
                                                state.serviceOverrides,
                                            ),
                                            onClick = { onOpenMovie(item.movie.ids.trakt) },
                                        )
                                    }
                                }
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
private fun ViewModeToggle(
    selected: UpcomingViewMode,
    onSelect: (UpcomingViewMode) -> Unit,
) {
    val zen = LocalZenColors.current
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(zen.mutedFill)
            .border(1.dp, zen.glassBorder, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UpcomingViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(zen.iridescentBrush)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(mode) },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) zen.onAccent else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpcomingCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    releaseCounts: Map<LocalDate, Int>,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val days = remember(month, firstDayOfWeek) { monthCalendarDays(month, firstDayOfWeek) }
    val weekdayLabels = remember(firstDayOfWeek, locale) {
        (0L until 7L).map { offset ->
            firstDayOfWeek.plus(offset).getDisplayName(JavaTextStyle.SHORT, locale)
        }
    }
    val swipeModifier = Modifier.pointerInput(month, canGoPrevious, canGoNext) {
        var dragDistance = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragDistance = 0f },
            onHorizontalDrag = { _, dragAmount -> dragDistance += dragAmount },
            onDragEnd = {
                when {
                    dragDistance <= -MONTH_SWIPE_THRESHOLD && canGoNext -> onNext()
                    dragDistance >= MONTH_SWIPE_THRESHOLD && canGoPrevious -> onPrevious()
                }
            },
            onDragCancel = { dragDistance = 0f },
        )
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(swipeModifier),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(
                    enabled = canGoPrevious,
                    description = "Previous month",
                    icon = Icons.Outlined.ChevronLeft,
                    onClick = onPrevious,
                )
                Text(
                    text = formatMonth(month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MonthArrow(
                    enabled = canGoNext,
                    description = "Next month",
                    icon = Icons.Outlined.ChevronRight,
                    onClick = onNext,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            inMonth = YearMonth.from(day) == month,
                            selected = day == selectedDate,
                            isToday = day == today,
                            releaseCount = releaseCounts[day] ?: 0,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectDate(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(
    enabled: Boolean,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.35f),
        shape = CircleShape,
        strong = true,
        onClick = if (enabled) onClick else null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    releaseCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zen = LocalZenColors.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val textColor = when {
        selected -> zen.onAccent
        !inMonth -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        else -> colors.onSurface
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(shape)
            .then(if (selected) Modifier.background(zen.iridescentBrush) else Modifier)
            .then(
                if (isToday && !selected) {
                    Modifier.border(1.dp, zen.glassBorderStrong, shape)
                } else {
                    Modifier
                },
            )
            .then(
                if (inMonth) {
                    Modifier.selectable(selected = selected, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
            if (inMonth && releaseCount > 0) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(minOf(releaseCount, 3)) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (selected) zen.onAccent else colors.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEmptyDay(message: String) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatRelativeDay(date),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (count > 0) {
            Pill(text = "$count ${if (count == 1) "movie" else "movies"}")
        }
    }
}

@Composable
private fun UndatedHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Date to be announced", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Trakt has no release date for these yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Pill(text = "$count")
    }
}

/**
 * One movie with all of its upcoming releases. The releases on [day], the day
 * the row is listed under, lead; releases on other days follow with their dates.
 */
@Composable
private fun ReleaseRow(
    item: ComingUpItem,
    day: LocalDate,
    service: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (onDay, otherDays) = item.events.partition { it.date == day }
    val headline = onDay.firstOrNull() ?: item.next
    val relative = relativeDaysLabel(headline.date)

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterImage(
                movie = item.movie,
                modifier = Modifier
                    .width(50.dp)
                    .height(75.dp),
                cornerRadius = 12.dp,
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                onDay.forEach { event ->
                    Text(
                        text = releaseDescription(event.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                otherDays.forEach { event ->
                    Text(
                        text = "${event.kind.label} · ${formatDate(event.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                if (service != null) {
                    Spacer(Modifier.height(8.dp))
                    ServiceLine(network = service)
                }
            }

            Spacer(Modifier.width(8.dp))

            Pill(text = relative ?: headline.kind.label, emphasis = true)
        }
    }
}

@Composable
private fun UndatedRow(
    entry: WatchlistMovie,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                movie = entry.movie,
                modifier = Modifier
                    .width(40.dp)
                    .height(60.dp),
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.movie.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.movie.year?.toString() ?: "Year unknown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun releaseDescription(kind: ReleaseKind): String = when (kind) {
    ReleaseKind.THEATRICAL -> "In theatres"
    ReleaseKind.DIGITAL -> "Digital release · rent, buy or stream"
    ReleaseKind.PHYSICAL -> "Blu-ray & DVD release"
    ReleaseKind.TV -> "TV premiere"
}
