package ca.gibert.movietracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gibert.movietracker.data.Movie
import coil.compose.AsyncImage

@Composable
fun PosterImage(
    movie: Movie?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val url = movie?.images?.poster?.firstOrNull()
        ?: movie?.images?.thumb?.firstOrNull()
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surfaceContainerHighest,
                        colors.surfaceContainerHigh,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Movie,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(28.dp),
        )

        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = traktImageUrl(url),
                contentDescription = movie?.title?.let { "Poster for $it" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MovieBackdrop(
    movie: Movie?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val url = movie?.images?.fanart?.firstOrNull()
        ?: movie?.images?.thumb?.firstOrNull()

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(colors.primaryContainer, colors.surfaceContainerHigh),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Movie,
            contentDescription = null,
            tint = colors.onPrimaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp),
        )

        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = traktImageUrl(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun traktImageUrl(url: String): String = when {
    url.startsWith("http://") || url.startsWith("https://") -> url
    url.startsWith("//") -> "https:$url"
    url.startsWith("/") -> "https://media.trakt.tv$url"
    else -> "https://$url"
}
