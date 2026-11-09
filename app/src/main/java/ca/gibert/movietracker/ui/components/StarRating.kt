package ca.gibert.movietracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Star color used for every rating in the app. */
val StarColor = Color(0xFFFFC857)

/**
 * Five stars for Trakt's 1–10 scale, so each point is half a star.
 *
 * With [onRate], each star has a left half (odd score) and a right half (even
 * score) to tap; tapping the current score clears it (reported as null).
 */
@Composable
fun StarRating(
    rating: Int?,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
    onRate: ((Int?) -> Unit)? = null,
) {
    val value = rating?.coerceIn(0, 10) ?: 0
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Row(
        modifier = modifier.semantics {
            contentDescription = if (value == 0) "Not rated" else "Rated ${formatStars(value)} of 5 stars"
        },
    ) {
        for (star in 1..5) {
            val icon = when {
                value >= star * 2 -> Icons.Filled.Star
                value == star * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Box(Modifier.size(starSize)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (value >= star * 2 - 1) StarColor else empty,
                    modifier = Modifier.fillMaxSize(),
                )
                if (onRate != null) {
                    Row(Modifier.fillMaxSize()) {
                        listOf(star * 2 - 1, star * 2).forEach { score ->
                            Box(
                                modifier = Modifier
                                    .width(starSize / 2)
                                    .fillMaxHeight()
                                    .clickable { onRate(if (score == value) null else score) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 7 → "3.5", 8 → "4". */
fun formatStars(rating: Int): String =
    if (rating % 2 == 0) "${rating / 2}" else "${rating / 2}.5"
