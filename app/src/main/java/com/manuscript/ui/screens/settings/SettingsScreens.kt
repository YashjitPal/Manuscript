package com.manuscript.ui.screens.settings

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import com.manuscript.BuildConfig
import com.manuscript.core.data.Account
import com.manuscript.core.data.AccountKind
import com.manuscript.core.data.AppSettings
import com.manuscript.core.data.PaletteStyle
import com.manuscript.core.data.ReadingFont
import com.manuscript.core.data.ReadingWidth
import com.manuscript.core.data.ThemeMode
import com.manuscript.core.model.NUser
import com.manuscript.core.net.NOTION_VERSION
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalContainer
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.ConfirmDialog
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.MorphShape
import com.manuscript.ui.components.ShapeBadge
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.navigation.AboutRoute
import com.manuscript.ui.navigation.AccountsRoute
import com.manuscript.ui.navigation.AppearanceRoute
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.PeopleRoute
import com.manuscript.ui.navigation.ReadingRoute
import com.manuscript.ui.navigation.StorageRoute
import com.manuscript.ui.navigation.TrashRoute
import com.manuscript.core.OAuthPhase
import com.manuscript.ui.screens.onboarding.ConnectScreen
import com.manuscript.ui.screens.onboarding.isBusy
import com.manuscript.ui.screens.onboarding.rememberOAuthLauncher
import com.manuscript.ui.theme.GoogleSansCodeFamily
import com.manuscript.ui.theme.LocalAppSettings
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.NewsreaderFamily
import com.manuscript.ui.theme.SeedColors
import com.manuscript.ui.theme.generateScheme
import com.manuscript.ui.theme.googleSansFlex
import com.manuscript.ui.theme.isAppInDarkTheme
import com.manuscript.ui.util.openUrl
import kotlinx.coroutines.launch

@Composable
private fun SettingsScaffold(title: String, content: LazyListScope.() -> Unit) {
    val navigator = LocalNavigator.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { if (navigator.backStack.size > 1) IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).wrapContentWidth().widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            content = content,
        )
    }
}

private fun LazyListScope.group(title: String?, rows: List<@Composable (Int, Int) -> Unit>) {
    if (title != null) item {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { i, row -> row(i, rows.size) }
        }
    }
}

@Composable
private fun NavRow(index: Int, count: Int, icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { ShapeBadge(icon, size = 40.dp, polygon = MaterialShapes.Cookie6Sided, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer) },
        supportingContent = subtitle?.let { { Text(it) } },
    ) { Text(title) }
}

@Composable
private fun ToggleRow(index: Int, count: Int, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    SegmentedListItem(
        checked = checked,
        onCheckedChange = { haptics.toggle(it); onChange(it) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        enabled = enabled,
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(title) }
}

@Composable
private fun rememberUpdate(): ((AppSettings) -> AppSettings) -> Unit {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    return remember(container) { { transform -> scope.launch { container.settingsStore.update(transform) } } }
}

private fun kindLabel(account: Account) = when (account.kind) {
    AccountKind.DEMO -> "Demo workspace"
    AccountKind.OAUTH -> "Public connection (OAuth)"
    AccountKind.TOKEN -> if (account.isPersonal) "Personal access token" else "Internal connection"
}

@Composable
fun SettingsScreen() {
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val account = session.account
    SettingsScaffold("Settings") {
        item {
            Surface(
                onClick = { navigator.navigate(AccountsRoute) },
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(NUser(account.userId ?: account.id, account.userName, account.userAvatar, "person"), 60.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.displayUser, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(account.displayWorkspace, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(kindLabel(account), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Icon(Icons.Rounded.SwitchAccount, "Switch account", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        group("Personalize", listOf(
            { i, n -> NavRow(i, n, Icons.Rounded.Palette, "Appearance", "Theme, color, typography and motion") { navigator.navigate(AppearanceRoute) } },
            { i, n -> NavRow(i, n, Icons.AutoMirrored.Rounded.ChromeReaderMode, "Reading & editing", "Fonts, text size, layout and behavior") { navigator.navigate(ReadingRoute) } },
        ))
        group("Workspace", listOf(
            { i, n -> NavRow(i, n, Icons.Rounded.SwitchAccount, "Accounts & workspaces", "Switch, add or remove workspaces") { navigator.navigate(AccountsRoute) } },
            { i, n -> NavRow(i, n, Icons.Rounded.Group, "People", "Members and integrations") { navigator.navigate(PeopleRoute) } },
            { i, n -> NavRow(i, n, Icons.Rounded.Delete, "Trash", "Restore deleted pages") { navigator.navigate(TrashRoute) } },
            { i, n -> NavRow(i, n, Icons.Rounded.Storage, "Storage & offline", "Cached pages and images") { navigator.navigate(StorageRoute) } },
        ))
        group(null, listOf(
            { i, n -> NavRow(i, n, Icons.Rounded.Info, "About Manuscript", "Version ${BuildConfig.VERSION_NAME}") { navigator.navigate(AboutRoute) } },
        ))
    }
}

@Composable
fun AppearanceScreen() {
    val settings = LocalAppSettings.current
    val update = rememberUpdate()
    val haptics = LocalHaptics.current
    val dark = isAppInDarkTheme(settings)
    SettingsScaffold("Appearance") {
        item { ThemePreview(settings) }
        item {
            Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    ToggleButton(
                        checked = settings.themeMode == mode,
                        onCheckedChange = { haptics.segment(); update { it.copy(themeMode = mode) } },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            ThemeMode.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }
        group(null, listOf(
            { i, n -> ToggleRow(i, n, "Pure black", "Use true black backgrounds in dark theme", settings.pureBlack, enabled = dark) { v -> update { it.copy(pureBlack = v) } } },
            { i, n -> ToggleRow(i, n, "Wallpaper colors", if (Build.VERSION.SDK_INT >= 31) "Match your system's dynamic color" else "Requires Android 12", settings.dynamicColor, enabled = Build.VERSION.SDK_INT >= 31) { v -> update { it.copy(dynamicColor = v) } } },
        ))
        item {
            Text("Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                SeedColors.forEach { (name, color) ->
                    val argb = color.toArgb().toLong() and 0xFFFFFFFFL
                    val selected = !settings.dynamicColor && settings.seed == argb
                    SeedSwatch(name, color, selected) { haptics.click(); update { it.copy(seed = argb, dynamicColor = false) } }
                }
            }
        }
        item {
            Text("Palette style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaletteStyle.entries.forEach { style ->
                    PaletteCard(style, settings, selected = settings.paletteStyle == style && !settings.dynamicColor) {
                        haptics.segment(); update { it.copy(paletteStyle = style, dynamicColor = false) }
                    }
                }
            }
        }
        item {
            SliderBlock("Contrast", when { settings.contrast >= 0.75 -> "High"; settings.contrast >= 0.25 -> "Medium"; else -> "Standard" }) {
                Slider(value = settings.contrast.toFloat(), onValueChange = { v -> update { it.copy(contrast = v.toDouble()) } }, valueRange = 0f..1f, steps = 1)
            }
        }
        item {
            SliderBlock("Font roundness", "${settings.roundness.toInt()}") {
                val family = remember(settings.roundness) { googleSansFlex(settings.roundness) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aa",
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.width(80.dp),
                    )
                    Slider(value = settings.roundness, onValueChange = { v -> update { it.copy(roundness = v) } }, valueRange = 0f..100f, modifier = Modifier.weight(1f))
                }
            }
        }
        group("Motion", listOf(
            { i, n -> ToggleRow(i, n, "Reduce motion", "Use calmer, less springy animations", settings.reduceMotion) { v -> update { it.copy(reduceMotion = v) } } },
            { i, n -> ToggleRow(i, n, "Haptic feedback", "Subtle vibrations for actions", settings.haptics) { v -> update { it.copy(haptics = v) } } },
        ))
    }
}

@Composable
private fun SliderBlock(title: String, value: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun SeedSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, label = "swatch")
    val morph = remember { androidx.graphics.shapes.Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(52.dp).clip(MorphShape(morph, progress)).background(color), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
        }
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun PaletteCard(style: PaletteStyle, settings: AppSettings, selected: Boolean, onClick: () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val scheme = remember(style, settings.seed, dark) { generateScheme(Color(settings.seed), style, dark, 0.0) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.width(132.dp).border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(if (selected) 28.dp else 20.dp)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { Box(Modifier.size(28.dp).clip(CircleShape).background(it)) }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(scheme.primaryContainer))
            Spacer(Modifier.height(10.dp))
            Text(style.label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
        }
    }
}

@Composable
private fun ThemePreview(settings: AppSettings) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).animateContentSize()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(Icons.Rounded.Palette, polygon = MaterialShapes.Flower, size = 84.dp, rotate = !settings.reduceMotion)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Your Manuscript", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text("Live preview of colors, type and shapes.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("Primary") }
                    FilledTonalButton(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("Tonal") }
                }
            }
        }
    }
}

@Composable
fun ReadingScreen() {
    val settings = LocalAppSettings.current
    val update = rememberUpdate()
    val haptics = LocalHaptics.current
    SettingsScaffold("Reading & editing") {
        item {
            Text("Page font", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadingFont.entries.forEach { font ->
                    val selected = settings.readingFont == font
                    Surface(
                        onClick = { haptics.segment(); update { it.copy(readingFont = font) } },
                        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Ag",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = when (font) {
                                        ReadingFont.SANS -> googleSansFlex(settings.roundness)
                                        ReadingFont.SERIF -> NewsreaderFamily
                                        ReadingFont.MONO -> GoogleSansCodeFamily
                                    },
                                ),
                            )
                            Text(font.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item {
            SliderBlock("Text size", "${(settings.textScale * 100).toInt()}%") {
                Slider(value = settings.textScale, onValueChange = { v -> update { it.copy(textScale = v) } }, valueRange = 0.85f..1.35f, steps = 9)
            }
        }
        item {
            Text("Content width", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                ReadingWidth.entries.forEachIndexed { i, w ->
                    ToggleButton(
                        checked = settings.readingWidth == w,
                        onCheckedChange = { haptics.segment(); update { it.copy(readingWidth = w) } },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            ReadingWidth.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(w.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        group("Pages", listOf(
            { i, n -> ToggleRow(i, n, "Small text", "Fit more on screen", settings.smallText) { v -> update { it.copy(smallText = v) } } },
            { i, n -> ToggleRow(i, n, "Show page covers", null, settings.showCovers) { v -> update { it.copy(showCovers = v) } } },
            { i, n -> ToggleRow(i, n, "Outline on large screens", "Show headings beside the page", settings.showOutline) { v -> update { it.copy(showOutline = v) } } },
            { i, n -> ToggleRow(i, n, "Wrap code", "Wrap long lines in code blocks", settings.wrapCode) { v -> update { it.copy(wrapCode = v) } } },
        ))
        group("Behavior", listOf(
            { i, n -> ToggleRow(i, n, "Open links in the app", "Use an in-app browser for web links", settings.openLinksInApp) { v -> update { it.copy(openLinksInApp = v) } } },
            { i, n -> ToggleRow(i, n, "Side peek for database rows", "Open rows beside the database on large screens", settings.sidePeek) { v -> update { it.copy(sidePeek = v) } } },
            { i, n -> ToggleRow(i, n, "Confirm before deleting", null, settings.confirmDelete) { v -> update { it.copy(confirmDelete = v) } } },
        ))
    }
}

@Composable
fun AccountsScreen() {
    val container = LocalContainer.current
    val current = LocalSession.current.account
    val accounts by container.accountList.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val launchOAuth = rememberOAuthLauncher()
    val phase by container.oauthPhase.collectAsState()
    val messenger = LocalMessenger.current
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    var signingOut by remember { mutableStateOf<Account?>(null) }
    LaunchedEffect(phase) {
        if (!adding) (phase as? OAuthPhase.Failed)?.let { messenger.show(it.message, long = true); container.clearOAuthError() }
    }
    SettingsScaffold("Accounts") {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), modifier = Modifier.padding(top = 8.dp)) {
                accounts.forEachIndexed { i, account ->
                    SegmentedListItem(
                        selected = account.id == current.id,
                        onClick = { haptics.confirm(); scope.launch { container.switchTo(account.id) } },
                        shapes = ListItemDefaults.segmentedShapes(i, accounts.size),
                        leadingContent = { UserAvatar(NUser(account.userId ?: account.id, account.userName, account.userAvatar, "person"), 44.dp) },
                        supportingContent = { Text("${account.displayWorkspace} · ${kindLabel(account)}") },
                        trailingContent = {
                            Row {
                                if (account.kind == AccountKind.OAUTH) IconButton(
                                    onClick = {
                                        scope.launch {
                                            container.accounts.oauthConfig(account.id)?.let(launchOAuth)
                                                ?: messenger.show("Sign out and back in to change which pages are shared.")
                                        }
                                    },
                                    enabled = !phase.isBusy,
                                ) { Icon(Icons.Rounded.Checklist, "Choose pages") }
                                IconButton(onClick = { renaming = account }) { Icon(Icons.Rounded.Edit, "Rename workspace") }
                                IconButton(onClick = { signingOut = account }) { Icon(Icons.AutoMirrored.Rounded.Logout, "Sign out") }
                            }
                        },
                    ) { Text(account.displayUser) }
                }
            }
        }
        item {
            FilledTonalButton(onClick = { adding = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add a workspace")
            }
        }
        item {
            Text(
                buildString {
                    append("Tokens are encrypted with a key held in this device's secure hardware.")
                    if (accounts.any { it.kind == AccountKind.OAUTH }) {
                        append(" Use the checklist button on a Notion sign-in to change which pages Manuscript can access.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
    if (adding) Dialog(onDismissRequest = { adding = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val before = remember { accounts }
        LaunchedEffect(accounts) { if (accounts != before) adding = false }
        ConnectScreen(onBack = { adding = false })
    }
    renaming?.let { account ->
        var name by remember { mutableStateOf(account.displayWorkspace) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Workspace name") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { scope.launch { container.renameWorkspace(account, name) }; renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    signingOut?.let { account ->
        ConfirmDialog(
            title = "Sign out?",
            message = "Remove ${account.displayWorkspace} from this device. Your Notion content isn't affected.",
            confirmLabel = "Sign out",
            destructive = true,
            icon = Icons.AutoMirrored.Rounded.Logout,
            onConfirm = { scope.launch { container.signOut(account.id) } },
            onDismiss = { signingOut = null },
        )
    }
}

@Composable
fun StorageScreen() {
    val session = LocalSession.current
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    val stats by produceState(0 to 0L, refresh) { value = session.workspace.cacheStats() }
    SettingsScaffold("Storage & offline") {
        item {
            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.Storage, polygon = MaterialShapes.Cookie12Sided, size = 64.dp, container = MaterialTheme.colorScheme.tertiary, content = MaterialTheme.colorScheme.onTertiary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${stats.first} pages available offline", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("${Fmt.bytes(stats.second)} of page content cached", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        group("Clear data", listOf(
            { i, n -> NavRow(i, n, Icons.Rounded.Delete, "Clear page cache", "Pages will be downloaded again when opened") {
                scope.launch { session.workspace.clearCache(); refresh++; messenger.show("Page cache cleared") }
            } },
            { i, n -> NavRow(i, n, Icons.Rounded.Delete, "Clear image cache", null) {
                SingletonImageLoader.get(context).let { loader -> loader.memoryCache?.clear(); loader.diskCache?.clear() }
                messenger.show("Image cache cleared")
            } },
            { i, n -> NavRow(i, n, Icons.Rounded.Delete, "Clear recently visited", null) { scope.launch { session.workspace.clearRecents(); messenger.show("History cleared") } } },
            { i, n -> NavRow(i, n, Icons.Rounded.Delete, "Clear search history", null) { scope.launch { session.workspace.clearSearches(); messenger.show("Search history cleared") } } },
        ))
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    SettingsScaffold("About") {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeBadge(Icons.AutoMirrored.Rounded.ChromeReaderMode, polygon = MaterialShapes.SoftBurst, size = 112.dp, rotate = true)
                Spacer(Modifier.height(16.dp))
                Text("Manuscript", style = MaterialTheme.typography.displaySmallEmphasized)
                Text("Version ${BuildConfig.VERSION_NAME} · Notion API $NOTION_VERSION", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A native Notion client built with Jetpack Compose and Material 3 Expressive.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp))
            }
        }
        group("Links", listOf(
            { i, n -> NavRow(i, n, Icons.AutoMirrored.Rounded.OpenInNew, "Notion developer docs", "developers.notion.com") { context.openUrl("https://developers.notion.com") } },
            { i, n -> NavRow(i, n, Icons.AutoMirrored.Rounded.OpenInNew, "Material 3 Expressive", "m3.material.io") { context.openUrl("https://m3.material.io") } },
        ))
        item {
            Text("Open source", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    listOf(
                        "Google Sans Flex, Google Sans Code and Newsreader — SIL Open Font License 1.1",
                        "Jetpack Compose, Material 3, Navigation 3, Room, DataStore, WorkManager, Media3, Glance — Apache 2.0",
                        "OkHttp, Coil, kotlinx.serialization, kotlinx.coroutines — Apache 2.0",
                        "Reorderable by Calvin Liang — Apache 2.0",
                        "MaterialKolor material-color-utilities — Apache 2.0",
                        "JLaTeXMath for Android — GPL-2.0 with classpath exception",
                        "Emoji data © Unicode, Inc. — Unicode License",
                        "Notion is a trademark of Notion Labs, Inc. Manuscript is not affiliated with Notion.",
                    ).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
