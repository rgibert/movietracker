package ca.gibert.movietracker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The softer, glassier palette used across the app. Material's colour scheme
 * still carries the text and control colours; these tokens add the translucent
 * surfaces and iridescent highlights that sit on top of the Zen background.
 */
@Immutable
data class ZenColors(
    val dark: Boolean,
    val onAccent: Color,
    val glassBrush: Brush,
    val glassStrongBrush: Brush,
    val glassBorder: Color,
    val glassBorderStrong: Color,
    val glassShadow: Color,
    val backgroundBrush: Brush,
    val iridescentBrush: Brush,
    val logoBrush: Brush,
    val mutedFill: Color,
    val topSheen: Color,
    val auroraSky: Color,
    val auroraBlush: Color,
    val auroraPeach: Color,
    val auroraMint: Color,
)

val LightZenColors = ZenColors(
    dark = true,
    onAccent = Color(0xFFF2F7FF),
    glassBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.14f),
            Color.White.copy(alpha = 0.06f),
        ),
    ),
    glassStrongBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.09f),
        ),
    ),
    glassBorder = Color.White.copy(alpha = 0.18f),
    glassBorderStrong = Color.White.copy(alpha = 0.30f),
    glassShadow = Color(0xFF02060F).copy(alpha = 0.50f),
    backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF0E1626),
            Color(0xFF13203A),
            Color(0xFF0D1730),
        ),
    ),
    iridescentBrush = Brush.linearGradient(
        listOf(
            Color(0xFF1E5496),
            Color(0xFF2F6FB8),
            Color(0xFF194576),
            Color(0xFF2E6BB2),
        ),
    ),
    logoBrush = Brush.linearGradient(
        listOf(
            Color(0xFF2A62A8),
            Color(0xFF3E86C9),
            Color(0xFF1D4E8B),
        ),
    ),
    mutedFill = Color.White.copy(alpha = 0.09f),
    topSheen = Color.White.copy(alpha = 0.14f),
    auroraSky = Color(0xFF3D84D4).copy(alpha = 0.40f),
    auroraBlush = Color(0xFF5566D6).copy(alpha = 0.26f),
    auroraPeach = Color(0xFF38A0D8).copy(alpha = 0.28f),
    auroraMint = Color(0xFF2C7FA6).copy(alpha = 0.26f),
)

val DarkZenColors = ZenColors(
    dark = true,
    onAccent = Color(0xFFF1F8FA),
    glassBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.035f),
        ),
    ),
    glassStrongBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.06f),
        ),
    ),
    glassBorder = Color.White.copy(alpha = 0.15f),
    glassBorderStrong = Color.White.copy(alpha = 0.24f),
    glassShadow = Color.Black.copy(alpha = 0.45f),
    backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF070C18),
            Color(0xFF0B1426),
            Color(0xFF080F1E),
        ),
    ),
    iridescentBrush = Brush.linearGradient(
        listOf(
            Color(0xFF16406F),
            Color(0xFF25598F),
            Color(0xFF123457),
            Color(0xFF2A6299),
        ),
    ),
    logoBrush = Brush.linearGradient(
        listOf(
            Color(0xFF2C6695),
            Color(0xFF31549A),
            Color(0xFF3F7EC4),
        ),
    ),
    mutedFill = Color.White.copy(alpha = 0.07f),
    topSheen = Color.White.copy(alpha = 0.08f),
    auroraSky = Color(0xFF1D5A96).copy(alpha = 0.42f),
    auroraBlush = Color(0xFF2E3A86).copy(alpha = 0.30f),
    auroraPeach = Color(0xFF1E6C96).copy(alpha = 0.28f),
    auroraMint = Color(0xFF1B5E7A).copy(alpha = 0.30f),
)

val LocalZenColors = staticCompositionLocalOf { LightZenColors }

@Composable
fun ZenBackground(modifier: Modifier = Modifier) {
    val zen = LocalZenColors.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(brush = zen.backgroundBrush)

        fun blob(center: Offset, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        blob(
            center = Offset(size.width * 0.92f, size.height * 0.04f),
            radius = size.width * 0.64f,
            color = zen.auroraSky,
        )
        blob(
            center = Offset(size.width * 0.02f, size.height * 0.38f),
            radius = size.width * 0.56f,
            color = zen.auroraBlush,
        )
        blob(
            center = Offset(size.width * 0.76f, size.height * 0.74f),
            radius = size.width * 0.62f,
            color = zen.auroraPeach,
        )
        blob(
            center = Offset(size.width * 0.12f, size.height * 0.97f),
            radius = size.width * 0.58f,
            color = zen.auroraMint,
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(zen.topSheen, Color.Transparent),
                endY = size.height * 0.34f,
            ),
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    brush: Brush? = null,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val zen = LocalZenColors.current
    val surface = modifier
        .then(
            if (zen.dark) {
                Modifier
            } else {
                Modifier.shadow(
                    elevation = if (strong) 18.dp else 12.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = zen.glassShadow,
                    spotColor = zen.glassShadow,
                )
            },
        )
        .clip(shape)
        .background(brush ?: if (strong) zen.glassStrongBrush else zen.glassBrush)
        .border(
            width = 1.dp,
            color = if (strong) zen.glassBorderStrong else zen.glassBorder,
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )

    Box(surface) {
        if (brush != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                zen.topSheen,
                                Color.Transparent,
                                Color.White.copy(alpha = if (zen.dark) 0.015f else 0.10f),
                            ),
                        ),
                    ),
            )
        }
        // Surfaces own their content colour: accent cards read with onAccent,
        // plain glass cards with onSurface, so unstyled text is never black.
        CompositionLocalProvider(
            LocalContentColor provides if (brush != null) {
                zen.onAccent
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            content()
        }
    }
}

@Composable
fun ZenIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.55f),
        shape = CircleShape,
        strong = true,
        onClick = if (enabled) onClick else null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
