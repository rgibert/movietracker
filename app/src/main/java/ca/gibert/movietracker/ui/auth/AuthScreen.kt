package ca.gibert.movietracker.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.movietracker.data.AppContainer
import ca.gibert.movietracker.data.AuthState
import ca.gibert.movietracker.data.DeviceLoginState
import ca.gibert.movietracker.ui.copyText
import ca.gibert.movietracker.ui.openUrl
import ca.gibert.movietracker.ui.theme.GlassSurface
import ca.gibert.movietracker.ui.theme.LocalZenColors
import kotlinx.coroutines.delay
import java.util.Locale

private const val CREATE_APP_URL = "https://app.trakt.tv/settings/apps/api/new"

@Composable
fun AuthScreen(
    container: AppContainer,
    loggedOut: AuthState.LoggedOut?,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(container)),
) {
    val loggedOutState by viewModel.authState.collectAsStateWithLifecycle()
    val deviceState by viewModel.deviceLogin.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingClientId by rememberSaveable { mutableStateOf(loggedOut == null) }
    var clientSecret by rememberSaveable { mutableStateOf("") }

    val state = loggedOutState
    val showSetup = state is AuthState.NeedsClientId || editingClientId

    val zen = LocalZenColors.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            GlassSurface(
                modifier = Modifier.size(84.dp),
                shape = RoundedCornerShape(28.dp),
                brush = zen.logoBrush,
                strong = true,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = zen.onAccent,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "YOUR MOVIES, SIMPLIFIED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Movie Tracker",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "A simple, free Trakt companion for the movies you want to watch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(32.dp))

            if (showSetup) {
                SetupCard(
                    initialClientId = (state as? AuthState.LoggedOut)?.clientId.orEmpty(),
                    clientSecret = clientSecret,
                    onSecretChange = { clientSecret = it },
                    onContinue = { clientId ->
                        editingClientId = false
                        viewModel.saveClientId(clientId, clientSecret)
                    },
                    onOpenCreateApp = { context.openUrl(CREATE_APP_URL) },
                )
            } else {
                ConnectCard(
                    state = state as AuthState.LoggedOut,
                    deviceState = deviceState,
                    onConnect = { viewModel.beginDeviceLogin() },
                    onCancel = { viewModel.cancelDeviceLogin() },
                    onChangeApp = { editingClientId = true },
                    onOpenVerification = { context.openUrl(it) },
                    onCopyCode = { code ->
                        context.copyText("Trakt activation code", code)
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Unofficial app. Your Trakt password is never shared with Movie Tracker. " +
                    "Data is cached on this device so your lists open quickly.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupCard(
    initialClientId: String,
    clientSecret: String,
    onSecretChange: (String) -> Unit,
    onContinue: (String) -> Unit,
    onOpenCreateApp: () -> Unit,
) {
    var clientId by rememberSaveable(initialClientId) { mutableStateOf(initialClientId) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var secretVisible by rememberSaveable { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        strong = true,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Step 1 · Add your Trakt API app",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Trakt requires every app to have its own free API key. Create one " +
                    "with your Trakt account, then paste the Client ID below. No client " +
                    "secret is needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Looks like a long string of letters and numbers.")
                },
            )
            TextButton(
                onClick = onOpenCreateApp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = 4.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Create a Trakt API app")
            }

            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = 4.dp,
                ),
            ) {
                Text(if (showAdvanced) "Hide advanced" else "Advanced (optional)")
            }

            if (showAdvanced) {
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = onSecretChange,
                    label = { Text("Client secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (secretVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                imageVector = if (secretVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (secretVisible) {
                                    "Hide client secret"
                                } else {
                                    "Show client secret"
                                },
                            )
                        }
                    },
                    supportingText = {
                        Text("Only needed if your Trakt app still requires the legacy device flow secret.")
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onContinue(clientId.trim()) },
                enabled = clientId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun ConnectCard(
    state: AuthState.LoggedOut,
    deviceState: DeviceLoginState,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onChangeApp: () -> Unit,
    onOpenVerification: (String) -> Unit,
    onCopyCode: (String) -> Unit,
) {
    val zen = LocalZenColors.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        strong = true,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Step 2 · Connect your account",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Movie Tracker will show you a short code. Enter it on Trakt to approve " +
                    "this app. The code expires after a few minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(zen.mutedFill)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "API app",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = maskClientId(state.clientId),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = onChangeApp) {
                    Text("Change")
                }
            }

            if (!state.message.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            when (deviceState) {
                DeviceLoginState.Idle -> {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Connect with Trakt")
                    }
                }

                DeviceLoginState.Starting -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Requesting a code from Trakt…")
                    }
                }

                is DeviceLoginState.Waiting -> {
                    DeviceCodeCard(
                        state = deviceState,
                        onOpenVerification = onOpenVerification,
                        onCopyCode = onCopyCode,
                        onCancel = onCancel,
                    )
                }

                is DeviceLoginState.Failed -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = deviceState.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Try again")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Your Trakt password is never shared with this app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceCodeCard(
    state: DeviceLoginState.Waiting,
    onOpenVerification: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val zen = LocalZenColors.current
    var remainingMillis by remember(state.expiresAt) {
        mutableLongStateOf((state.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
    }

    LaunchedEffect(state.expiresAt) {
        while (remainingMillis > 0) {
            delay(1_000)
            remainingMillis = (state.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        GlassSurface(
            brush = zen.iridescentBrush,
            strong = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Enter this code on Trakt",
                    style = MaterialTheme.typography.labelLarge,
                    color = zen.onAccent.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.userCode,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                    ),
                    fontFamily = FontFamily.Monospace,
                    color = zen.onAccent,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        remainingMillis <= 0 -> "Code expired"
                        else -> "Expires in ${formatCountdown(remainingMillis)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = zen.onAccent.copy(alpha = 0.62f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onCopyCode(state.userCode) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Copy code")
            }
            Button(
                onClick = { onOpenVerification(state.verificationUrl) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                ),
            ) {
                Text("Open Trakt")
            }
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Waiting for approval…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1_000).coerceAtLeast(0)
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60,
    )
}

private fun maskClientId(clientId: String): String {
    if (clientId.length <= 12) return clientId
    return "${clientId.take(6)}…${clientId.takeLast(4)}"
}
