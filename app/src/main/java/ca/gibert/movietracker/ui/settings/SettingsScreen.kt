package ca.gibert.movietracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.movietracker.BuildConfig
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.RegionStore
import ca.gibert.movietracker.data.User
import ca.gibert.movietracker.ui.components.Pill
import ca.gibert.movietracker.ui.openUrl
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.ui.theme.LocalZenColors
import coil.compose.AsyncImage
import java.util.Locale

private const val TRAKT_URL = "https://trakt.tv"
private const val TRAKT_API_APPS_URL = "https://app.trakt.tv/settings/apps/api"
private const val TMDB_URL = "https://www.themoviedb.org"

@Composable
fun SettingsScreen(
    container: AppContainer,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmForgetApp by rememberSaveable { mutableStateOf(false) }
    var editingRegion by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(
                profile = state.profile,
                isLoading = state.isLoadingProfile,
                error = state.profileError,
                onRetry = viewModel::refreshProfile,
            )

            SettingsSection(title = "Account") {
                SettingsRow(
                    icon = Icons.Outlined.Key,
                    title = "Trakt API app",
                    subtitle = state.clientId.takeIf { it.isNotBlank() }?.let(::maskClientId)
                        ?: "Not configured",
                    onClick = { confirmForgetApp = true },
                    trailing = "Change",
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "Disconnect account",
                    subtitle = "Sign out and remove saved data from this device",
                    onClick = { confirmSignOut = true },
                    enabled = !state.isBusy,
                )
            }

            SettingsSection(title = "Where you watch") {
                SettingsRow(
                    icon = Icons.Outlined.Public,
                    title = "Region",
                    subtitle = state.region.takeIf { it.isNotBlank() }?.let { code ->
                        val name = Locale("", code).getDisplayCountry(Locale.getDefault())
                        if (name.isNotBlank() && name != code) "$name ($code)" else code
                    } ?: "Device default",
                    onClick = { editingRegion = true },
                    trailing = "Change",
                )
            }

            if (state.serviceOverrides.isNotEmpty()) {
                SettingsSection(title = "Pinned services") {
                    state.serviceOverrides
                        .sortedBy { it.title.lowercase() }
                        .forEachIndexed { index, override ->
                            if (index > 0) RowDivider()
                            SettingsRow(
                                icon = Icons.Outlined.PushPin,
                                title = override.title,
                                subtitle = "Watching on ${override.service}",
                                onClick = { viewModel.resetService(override) },
                                trailing = "Remove",
                            )
                        }
                }
            }

            SettingsSection(title = "Saved data") {
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Clear cached movies",
                    subtitle = "Lists, release dates and services refresh the next time you open them",
                    onClick = viewModel::clearCache,
                    enabled = !state.isBusy,
                )
            }

            SettingsSection(title = "About") {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Movie Tracker",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · movies only",
                    showChevron = false,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open Trakt",
                    subtitle = TRAKT_URL,
                    onClick = { context.openUrl(TRAKT_URL) },
                    showChevron = false,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Manage API apps on Trakt",
                    subtitle = TRAKT_API_APPS_URL,
                    onClick = { context.openUrl(TRAKT_API_APPS_URL) },
                    showChevron = false,
                )
                if (state.hasProviderData) {
                    RowDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = "Streaming data: TMDB & JustWatch",
                        subtitle = "This product uses the TMDB API but is not endorsed or " +
                            "certified by TMDB.",
                        onClick = { context.openUrl(TMDB_URL) },
                        showChevron = false,
                    )
                }
            }

            Text(
                text = "Unofficial app. Your Trakt username, password and tokens never " +
                    "leave this device except to talk to Trakt's API.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Disconnect from Trakt?",
            message = "You'll need to approve this app again to see your movies. " +
                "Saved data on this device will be cleared.",
            confirmLabel = "Disconnect",
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (editingRegion) {
        RegionDialog(
            current = state.region,
            onSave = { code ->
                editingRegion = false
                viewModel.setRegion(code)
            },
            onDismiss = { editingRegion = false },
        )
    }

    if (confirmForgetApp) {
        ConfirmDialog(
            title = "Change the Trakt API app?",
            message = "This disconnects your account and removes the saved API app " +
                "settings from this device. You'll be asked for a Client ID again.",
            confirmLabel = "Change app",
            onConfirm = {
                confirmForgetApp = false
                viewModel.forgetApiApp()
            },
            onDismiss = { confirmForgetApp = false },
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 67.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun ProfileCard(
    profile: User?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val zen = LocalZenColors.current

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        brush = zen.iridescentBrush,
        strong = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(profile)

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                when {
                    profile != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name?.takeIf { it.isNotBlank() }
                                    ?: profile.username,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                color = zen.onAccent,
                            )
                            if (profile.vip) {
                                Spacer(Modifier.width(8.dp))
                                Pill("VIP")
                            }
                        }
                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = zen.onAccent.copy(alpha = 0.64f),
                        )
                    }

                    isLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = zen.onAccent,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Loading your Trakt profile…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = zen.onAccent.copy(alpha = 0.72f),
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = "Trakt account connected",
                            style = MaterialTheme.typography.titleSmall,
                            color = zen.onAccent,
                        )
                        if (error != null) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = zen.onAccent.copy(alpha = 0.64f),
                            )
                        }
                    }
                }
            }

            if (error != null && !isLoading) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Avatar(profile: User?) {
    val zen = LocalZenColors.current
    val avatar = profile?.images?.avatar?.full
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(zen.logoBrush)
            .border(2.dp, zen.glassBorderStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = zen.onAccent,
            modifier = Modifier.size(30.dp),
        )
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            strong = true,
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val zen = LocalZenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (enabled) {
                        zen.logoBrush
                    } else {
                        androidx.compose.ui.graphics.SolidColor(colors.surfaceContainerHigh)
                    },
                )
                .border(
                    1.dp,
                    if (enabled) zen.glassBorder else colors.outlineVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) zen.onAccent else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        } else if (onClick != null && showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Edits the country used for release dates and streaming providers. */
@Composable
private fun RegionDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var region by rememberSaveable { mutableStateOf(current) }
    val code = region.trim().uppercase(Locale.ROOT)
    val valid = code.isEmpty() || (code.length == 2 && code.all { it in 'A'..'Z' })
    val countryName = remember(code) {
        if (code.length == 2) Locale("", code).getDisplayCountry(Locale.getDefault()) else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Region") },
        text = {
            Column {
                Text(
                    text = "Release dates and streaming services differ by country. Enter a " +
                        "two-letter country code, or leave it blank to follow this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it.take(2) },
                    label = { Text("Country code") },
                    placeholder = { Text(RegionStore.defaultRegion()) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onSave(code) }),
                    supportingText = {
                        Text(
                            when {
                                !valid -> "Use a code like US, CA or GB"
                                countryName.isNotBlank() && countryName != code -> countryName
                                else -> "For example US, CA or GB"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(code) }, enabled = valid) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConfirmDialog(
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun maskClientId(clientId: String): String {
    if (clientId.length <= 12) return clientId
    return "${clientId.take(6)}…${clientId.takeLast(4)}"
}
