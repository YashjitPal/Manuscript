package com.manuscript.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.manuscript.core.OAuthPhase
import com.manuscript.core.net.BuiltInOAuth
import com.manuscript.core.net.NotionOAuth
import com.manuscript.core.net.OAuthConfig
import com.manuscript.core.net.userMessage
import com.manuscript.ui.LocalContainer
import com.manuscript.ui.components.ShapeBadge
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.util.clipboardText
import com.manuscript.ui.util.openUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
private const val TOKENS_URL = "https://www.notion.so/developers/tokens"
private const val CONNECTIONS_URL = "https://app.notion.com/developers/connections"

@Composable
fun OnboardingFlow() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState > initialState
            (slideInHorizontally(spring(stiffness = 380f, dampingRatio = 0.86f)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260)))
                .togetherWith(slideOutHorizontally(spring(stiffness = 380f)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)))
        },
        label = "onboarding",
    ) { current ->
        if (current == 0) WelcomeScreen(onConnect = { step = 1 }) else ConnectScreen(onBack = { step = 0 })
    }
}

/** A blob that continuously morphs between Material shapes while slowly rotating. */
@Composable
fun MorphingBlob(
    polygons: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    holdMillis: Long = 1600,
    rotationPeriod: Int = 30000,
) {
    val morphs = remember(polygons) { polygons.indices.map { Morph(polygons[it], polygons[(it + 1) % polygons.size]) } }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(morphs) {
        while (true) {
            delay(holdMillis)
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 60f))
            index = (index + 1) % morphs.size
            progress.snapTo(0f)
        }
    }
    val spin = rememberInfiniteTransition(label = "blob")
    val rotation = spin.animateFloat(0f, 360f, infiniteRepeatable(tween(rotationPeriod, easing = LinearEasing)), label = "rotation")
    Box(
        modifier.drawWithCache {
            val path = android.graphics.Path()
            val composePath = path.asComposePath()
            val matrix = android.graphics.Matrix()
            val bounds = android.graphics.RectF()
            onDrawBehind {
                path.rewind()
                morphs[index].toPath(progress.value, path)
                matrix.setScale(size.width, size.height)
                path.transform(matrix)
                path.computeBounds(bounds, true)
                path.offset(size.width / 2 - bounds.centerX(), size.height / 2 - bounds.centerY())
                rotate(rotation.value) { drawPath(composePath, color) }
            }
        },
    )
}

@Composable
private fun HeroArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val big = maxWidth.coerceAtMost(maxHeight) * 0.78f
        MorphingBlob(
            listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny, MaterialShapes.Flower, MaterialShapes.SoftBurst),
            scheme.primaryContainer,
            Modifier.size(big).align(Alignment.Center).offset(x = big * 0.12f, y = -big * 0.05f),
        )
        MorphingBlob(
            listOf(MaterialShapes.Pill, MaterialShapes.Gem, MaterialShapes.Pentagon, MaterialShapes.Arch),
            scheme.tertiaryContainer,
            Modifier.size(big * 0.46f).align(Alignment.BottomStart).offset(x = big * 0.02f, y = -big * 0.04f),
            holdMillis = 2100,
            rotationPeriod = 22000,
        )
        MorphingBlob(
            listOf(MaterialShapes.Puffy, MaterialShapes.Cookie4Sided, MaterialShapes.Burst),
            scheme.secondaryContainer,
            Modifier.size(big * 0.3f).align(Alignment.TopStart).offset(x = big * 0.08f, y = big * 0.1f),
            holdMillis = 2600,
            rotationPeriod = 18000,
        )
        Text(
            "M",
            style = MaterialTheme.typography.displayLargeEmphasized.copy(fontWeight = FontWeight(900)),
            color = scheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.Center).offset(x = big * 0.12f, y = -big * 0.05f).graphicsLayer {
                scaleX = 2.4f
                scaleY = 2.4f
            },
        )
    }
}

@Composable
private fun WelcomeScreen(onConnect: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var startingDemo by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    val phase by container.oauthPhase.collectAsState()
    val launchOAuth = rememberOAuthLauncher()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 720.dp
            val heroHeight = maxHeight * 0.36f
            val content: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("Manuscript", style = MaterialTheme.typography.displayLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your Notion workspace, reimagined as a beautifully native Android app.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(28.dp))
                    Feature(Icons.Rounded.AutoAwesome, "Every block, beautifully rendered", "Rich text, code, math, tables, media, synced blocks and more.")
                    Feature(Icons.Rounded.TableChart, "Databases with real views", "Your saved table, board, calendar, timeline, gallery and chart views.")
                    Feature(Icons.Rounded.Devices, "Made for every screen", "Phones, foldables, tablets and Chromebooks — with keyboard shortcuts.")
                    Feature(Icons.Rounded.Palette, "Material 3 Expressive", "Dynamic color, custom palettes and fluid, springy motion.")
                    Spacer(Modifier.height(28.dp))
                    val oauth = BuiltInOAuth.config
                    if (oauth != null) {
                        NotionSignInButton(phase, onClick = { haptics.confirm(); container.clearOAuthError(); launchOAuth(oauth) })
                    } else {
                        Button(
                            onClick = { haptics.confirm(); onConnect() },
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                        ) {
                            Text("Connect your Notion", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            startingDemo = true
                            haptics.click()
                            scope.launch { container.startDemo() }
                        },
                        enabled = !startingDemo && !phase.isBusy,
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    ) {
                        if (startingDemo) LoadingIndicator(Modifier.size(28.dp))
                        else Text("Explore the demo workspace", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    }
                    if (oauth != null) {
                        TextButton(
                            onClick = { haptics.click(); onConnect() },
                            enabled = !phase.isBusy,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                        ) { Text("Use a token instead") }
                    }
                    AnimatedVisibility(phase is OAuthPhase.Failed) {
                        ErrorBanner((phase as? OAuthPhase.Failed)?.message.orEmpty(), Modifier.padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (oauth != null) "You approve access on notion.com and choose which pages Manuscript can see. Credentials stay encrypted on this device."
                            else "Your token is encrypted on this device and only sent to Notion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeroArt(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(48.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        content(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    HeroArt(Modifier.fillMaxWidth().height(heroHeight))
                    content(Modifier.fillMaxWidth().padding(bottom = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        ShapeBadge(
            icon,
            polygon = MaterialShapes.Cookie6Sided,
            size = 40.dp,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class Method(val label: String) { PERSONAL("Personal"), INTEGRATION("Internal"), OAUTH("OAuth app") }

val OAuthPhase.isBusy: Boolean get() = this == OAuthPhase.InBrowser || this == OAuthPhase.Exchanging

/** Opens Notion's consent screen in a Custom Tab; the result returns through MainActivity's OAuth callback. */
@Composable
fun rememberOAuthLauncher(): (OAuthConfig) -> Unit {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(container, context, scope) {
        { config -> scope.launch { context.openUrl(container.beginOAuth(config).toString()) } }
    }
}

@Composable
fun NotionSignInButton(phase: OAuthPhase, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = !phase.isBusy,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = modifier.fillMaxWidth().heightIn(min = height),
    ) {
        AnimatedContent(phase.isBusy, label = "sign-in") { busy ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (phase == OAuthPhase.Exchanging) "Signing you in…" else "Waiting for Notion…",
                        style = ButtonDefaults.textStyleFor(height),
                    )
                } else {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("N", style = MaterialTheme.typography.titleMediumEmphasized.copy(fontWeight = FontWeight.Black), color = Color.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Continue with Notion", style = ButtonDefaults.textStyleFor(height))
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) { Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun ConnectScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var method by rememberSaveable { mutableStateOf(Method.PERSONAL) }
    var token by rememberSaveable { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var clientId by rememberSaveable { mutableStateOf("") }
    var clientSecret by rememberSaveable { mutableStateOf("") }
    var redirect by rememberSaveable { mutableStateOf(NotionOAuth.DEFAULT_REDIRECT) }
    val phase by container.oauthPhase.collectAsState()
    val launchOAuth = rememberOAuthLauncher()
    val builtIn = BuiltInOAuth.config
    LaunchedEffect(phase) {
        (phase as? OAuthPhase.Failed)?.let {
            error = it.message
            haptics.reject()
            container.clearOAuthError()
        }
    }

    fun connect() {
        if (busy || phase.isBusy) return
        error = null
        if (method == Method.OAUTH) {
            if (clientId.isBlank() || clientSecret.isBlank() || redirect.isBlank()) {
                error = "Enter the client ID, client secret and redirect URI from your public connection."
                return
            }
            launchOAuth(OAuthConfig(clientId.trim(), clientSecret.trim(), redirect.trim()))
            return
        }
        if (token.isBlank()) {
            error = "Paste your token to continue."
            haptics.reject()
            return
        }
        busy = true
        scope.launch {
            runCatching { container.signInWithToken(token) }
                .onSuccess { haptics.confirm() }
                .onFailure {
                    error = it.userMessage()
                    haptics.reject()
                    busy = false
                }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                Spacer(Modifier.height(8.dp))
                ShapeBadge(Icons.Rounded.Key, polygon = MaterialShapes.Clover4Leaf, size = 72.dp, rotate = true)
                Spacer(Modifier.height(20.dp))
                Text("Connect Notion", style = MaterialTheme.typography.displaySmallEmphasized)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (builtIn != null) "Sign in with your Notion account, or connect with a token if you prefer."
                    else "Choose how Manuscript signs in. A personal access token is the easiest way to see everything you can see in Notion.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                if (builtIn != null) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Recommended", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Approve access on notion.com and pick the pages Manuscript can see. No tokens to copy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            NotionSignInButton(phase, onClick = { error = null; haptics.confirm(); launchOAuth(builtIn) })
                        }
                    }
                    Text(
                        "Or connect with a token",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                    Method.entries.forEachIndexed { index, m ->
                        ToggleButton(
                            checked = method == m,
                            onCheckedChange = { method = m; haptics.segment(); error = null },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                Method.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                AnimatedContent(method, label = "method") { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (m) {
                            Method.PERSONAL -> {
                                Step(1, "Open the Developer portal", "Create a personal access token with Notion API access.") {
                                    TextButton(onClick = { context.openUrl(TOKENS_URL) }, shapes = ButtonDefaults.shapes()) {
                                        Text("Open personal access tokens")
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                    }
                                }
                                Step(2, "Copy the token", "It's shown once. It acts with your own Notion permissions.")
                                Step(3, "Paste it below", null)
                            }
                            Method.INTEGRATION -> {
                                Step(1, "Create an internal connection", "In the Developer portal, create a connection and copy its API token.") {
                                    TextButton(onClick = { context.openUrl(CONNECTIONS_URL) }, shapes = ButtonDefaults.shapes()) {
                                        Text("Open connections")
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                    }
                                }
                                Step(2, "Share pages with it", "In Notion, open a page → ••• → Connections → add your connection.")
                                Step(3, "Paste the token below", null)
                            }
                            Method.OAUTH -> {
                                Step(1, "Use your public connection", "Add this redirect URI to the connection's OAuth settings, then enter its credentials.")
                                OutlinedTextField(clientId, { clientId = it }, label = { Text("OAuth client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                                OutlinedTextField(
                                    clientSecret, { clientSecret = it }, label = { Text("OAuth client secret") }, singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                                )
                                OutlinedTextField(redirect, { redirect = it }, label = { Text("Redirect URI") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
                            }
                        }
                    }
                }
                AnimatedVisibility(method != Method.OAUTH) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; error = null },
                        label = { Text(if (method == Method.PERSONAL) "Personal access token" else "Internal connection token") },
                        placeholder = { Text("ntn_…") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { connect() }),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { reveal = !reveal }) {
                                    Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide" else "Show")
                                }
                                IconButton(onClick = { context.clipboardText()?.trim()?.let { token = it; haptics.tick() } }) {
                                    Icon(Icons.Rounded.ContentPaste, "Paste")
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    )
                }
                AnimatedVisibility(error != null) {
                    ErrorBanner(error.orEmpty(), Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.height(20.dp))
                val working = busy || (method == Method.OAUTH && phase.isBusy)
                Button(
                    onClick = ::connect,
                    enabled = !busy && !phase.isBusy,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                ) {
                    if (working) LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text(
                        if (method == Method.OAUTH) "Authorize with Notion" else "Connect",
                        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String?, size: Dp = 32.dp, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            action?.invoke()
        }
    }
}
