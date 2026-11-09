package ca.gibert.movietracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.AuthState
import ca.gibert.movietracker.ui.auth.AuthScreen
import ca.gibert.movietracker.ui.detail.MovieDetailScreen
import ca.gibert.movietracker.ui.diary.DiaryScreen
import ca.gibert.movietracker.ui.search.SearchScreen
import ca.gibert.movietracker.ui.settings.SettingsScreen
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.ui.theme.LocalZenColors
import ca.gibert.movietracker.ui.theme.ZenBackground
import ca.gibert.movietracker.ui.theme.ZenIconButton
import ca.gibert.movietracker.ui.upcoming.UpcomingScreen
import ca.gibert.movietracker.ui.watch.WatchScreen

private object Routes {
    const val WATCH = "watch"
    const val UPCOMING = "upcoming"
    const val DIARY = "diary"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val MOVIE = "movie/{movieId}"
    const val MOVIE_ARG = "movieId"

    fun movie(movieId: Int) = "movie/$movieId"
}

private data class TabDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabDestination(Routes.WATCH, "To Watch", Icons.Outlined.Theaters),
    TabDestination(Routes.UPCOMING, "Coming Up", Icons.Outlined.CalendarMonth),
    TabDestination(Routes.DIARY, "Diary", Icons.Outlined.AutoStories),
)

@Composable
fun MovieTrackerRoot(container: AppContainer) {
    val authState by container.authRepository.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        ZenBackground()
        // GlassSurface is a plain Box, so any Text/Icon without an explicit
        // colour would fall back to the default black content colour. Provide
        // the scheme's on-surface colour so unstyled content stays legible on
        // the dark blue background.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            when (val state = authState) {
                AuthState.Loading -> LoadingScreen()
                AuthState.NeedsClientId -> AuthScreen(container = container, loggedOut = null)
                is AuthState.LoggedOut -> AuthScreen(container = container, loggedOut = state)
                is AuthState.LoggedIn -> MainScaffold(container = container)
            }
        }
    }
}

@Composable
private fun MainScaffold(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTab = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            ZenTopBar(
                isTab = isTab,
                currentRoute = currentRoute,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onSearch = { navController.navigate(Routes.SEARCH) },
            )
        },
        bottomBar = {
            if (isTab) {
                ZenBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WATCH,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val openMovie: (Int) -> Unit = { movieId ->
                navController.navigate(Routes.movie(movieId))
            }
            val openSearch: () -> Unit = { navController.navigate(Routes.SEARCH) }

            composable(Routes.WATCH) {
                WatchScreen(
                    container = container,
                    onOpenMovie = openMovie,
                    onAddMovie = openSearch,
                )
            }

            composable(Routes.UPCOMING) {
                UpcomingScreen(
                    container = container,
                    onOpenMovie = openMovie,
                    onAddMovie = openSearch,
                )
            }

            composable(Routes.DIARY) {
                DiaryScreen(
                    container = container,
                    onOpenMovie = openMovie,
                    onAddMovie = openSearch,
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    container = container,
                    onOpenMovie = openMovie,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(container = container)
            }

            composable(
                route = Routes.MOVIE,
                arguments = listOf(
                    navArgument(Routes.MOVIE_ARG) { type = NavType.IntType },
                ),
            ) { entry ->
                val movieId = entry.arguments?.getInt(Routes.MOVIE_ARG) ?: return@composable
                MovieDetailScreen(
                    container = container,
                    movieId = movieId,
                )
            }
        }
    }
}

@Composable
private fun ZenTopBar(
    isTab: Boolean,
    currentRoute: String?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isTab) {
            BrandMark()
        } else {
            ZenIconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = if (isTab) {
                    "Movie Tracker"
                } else {
                    when (currentRoute) {
                        Routes.SETTINGS -> "Settings"
                        Routes.SEARCH -> "Add a movie"
                        Routes.MOVIE -> "Movie"
                        else -> "Movie Tracker"
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
            )
            if (!isTab) {
                Text(
                    text = when (currentRoute) {
                        Routes.SETTINGS -> "Account & saved data"
                        Routes.SEARCH -> "Search Trakt"
                        else -> "Watch, rate & review"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (isTab) {
            ZenIconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Add a movie",
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            ZenIconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BrandMark() {
    val zen = LocalZenColors.current
    GlassSurface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        brush = zen.logoBrush,
        strong = true,
    ) {
        Icon(
            imageVector = Icons.Outlined.Movie,
            contentDescription = null,
            tint = zen.onAccent,
            modifier = Modifier
                .align(Alignment.Center)
                .size(21.dp),
        )
    }
}

@Composable
private fun ZenBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            shape = CircleShape,
            strong = true,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    ZenNavItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(tab.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ZenNavItem(
    tab: TabDestination,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val zen = LocalZenColors.current
    val colors = MaterialTheme.colorScheme
    val contentColor by animateColorAsState(
        targetValue = if (selected) zen.onAccent else colors.onSurfaceVariant,
        label = "navItemColor",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(zen.iridescentBrush)
                        .border(1.dp, zen.glassBorderStrong, CircleShape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = tab.icon,
                // Unselected tabs show only their icon, so it carries the name.
                contentDescription = if (selected) null else tab.label,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
            // Three labelled tabs don't fit a phone-width bar, so only the
            // selected one spells out its name.
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
