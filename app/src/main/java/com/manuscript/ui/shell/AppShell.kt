package com.manuscript.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.manuscript.core.AppContainer
import com.manuscript.core.SessionState
import com.manuscript.core.data.Session
import com.manuscript.core.data.ThemeMode
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.core.util.NotionIds
import com.manuscript.ui.LocalContainer
import com.manuscript.ui.LocalSession
import com.manuscript.ui.LocalWideLayout
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.LinkHandler
import com.manuscript.ui.components.LocalLinkHandler
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.Messenger
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.navigation.AboutRoute
import com.manuscript.ui.navigation.AccountsRoute
import com.manuscript.ui.navigation.ActivityRoute
import com.manuscript.ui.navigation.AppNavigator
import com.manuscript.ui.navigation.AppearanceRoute
import com.manuscript.ui.navigation.DatabaseRoute
import com.manuscript.ui.navigation.FavoritesRoute
import com.manuscript.ui.navigation.HomeRoute
import com.manuscript.ui.navigation.LibraryRoute
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.MediaRoute
import com.manuscript.ui.navigation.PageRoute
import com.manuscript.ui.navigation.PeopleRoute
import com.manuscript.ui.navigation.ReadingRoute
import com.manuscript.ui.navigation.Route
import com.manuscript.ui.navigation.SearchRoute
import com.manuscript.ui.navigation.SettingsRoute
import com.manuscript.ui.navigation.StorageRoute
import com.manuscript.ui.navigation.Tab
import com.manuscript.ui.navigation.TrashRoute
import com.manuscript.ui.navigation.rememberAppNavigator
import com.manuscript.ui.open
import com.manuscript.ui.screens.activity.ActivityScreen
import com.manuscript.ui.screens.database.DatabaseScreen
import com.manuscript.ui.screens.home.HomeScreen
import com.manuscript.ui.screens.library.LocalTree
import com.manuscript.ui.screens.library.TreeState
import com.manuscript.ui.screens.library.treeItems
import com.manuscript.ui.screens.media.MediaViewerScreen
import com.manuscript.ui.screens.onboarding.OnboardingFlow
import com.manuscript.ui.screens.page.PageScreen
import com.manuscript.ui.screens.pickers.NewDatabaseSheet
import com.manuscript.ui.screens.pickers.NewPageSheet
import com.manuscript.ui.screens.search.SearchScreen
import com.manuscript.ui.screens.settings.AboutScreen
import com.manuscript.ui.screens.settings.AccountsScreen
import com.manuscript.ui.screens.settings.AppearanceScreen
import com.manuscript.ui.screens.settings.ReadingScreen
import com.manuscript.ui.screens.settings.SettingsScreen
import com.manuscript.ui.screens.settings.StorageScreen
import com.manuscript.ui.screens.library.LibraryScreen
import com.manuscript.ui.screens.workspace.FavoritesScreen
import com.manuscript.ui.screens.workspace.PeopleScreen
import com.manuscript.ui.screens.workspace.TrashScreen
import com.manuscript.ui.theme.LocalAppSettings
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalIsDark
import com.manuscript.ui.theme.ManuscriptTheme
import com.manuscript.ui.toRoute
import com.manuscript.ui.util.copyText
import com.manuscript.ui.util.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Requests that arrive from outside the composition: intents, launcher shortcuts and keyboard shortcuts. */
sealed interface ShellAction {
    data class OpenLink(val url: String) : ShellAction
    data class ShareText(val text: String, val subject: String?) : ShellAction
    data object NewPage : ShellAction
    data object QuickNote : ShellAction
    data object NewDatabase : ShellAction
    data object Search : ShellAction
    data object Settings : ShellAction
    data object Back : ShellAction
    data object ToggleSidebar : ShellAction
    data object ToggleTheme : ShellAction
    data class SelectTab(val tab: Tab) : ShellAction
}

object ShellActions {
    val pending = MutableStateFlow<ShellAction?>(null)
    fun post(action: ShellAction) {
        pending.value = action
    }
}

private data class NewPageRequest(val parent: NodeRef? = null, val title: String = "", val body: String = "", val quick: Boolean = false)

@Composable
fun ManuscriptRoot(container: AppContainer) {
    val settings by container.settings.collectAsStateWithLifecycle()
    LifecycleResumeEffect(container) {
        container.onAppResumed()
        onPauseOrDispose { }
    }
    ManuscriptTheme(settings) {
        val state by container.state.collectAsStateWithLifecycle()
        val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val messenger = remember(snackbar, scope) { Messenger(snackbar, scope) }
        CompositionLocalProvider(LocalContainer provides container, LocalMessenger provides messenger) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                AnimatedContent(
                    targetState = state,
                    contentKey = { s ->
                        when (s) {
                            SessionState.Loading -> "loading"
                            SessionState.SignedOut -> "signed-out"
                            is SessionState.Active -> "session-" + s.session.account.id
                        }
                    },
                    transitionSpec = { fadeIn(tween(420)) + scaleIn(initialScale = 0.96f, animationSpec = tween(420)) togetherWith fadeOut(tween(220)) },
                    label = "session",
                ) { s ->
                    when (s) {
                        SessionState.Loading -> Box(Modifier.fillMaxSize())
                        SessionState.SignedOut -> Box(Modifier.fillMaxSize()) {
                            OnboardingFlow()
                            MessengerHost(Modifier.align(Alignment.BottomCenter))
                        }
                        is SessionState.Active -> AppShell(s.session)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessengerHost(modifier: Modifier = Modifier) {
    SnackbarHost(
        LocalMessenger.current.host,
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(12.dp),
    ) { data ->
        Snackbar(
            data,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@Composable
fun AppShell(session: Session) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val dark = LocalIsDark.current
    val haptics = LocalHaptics.current
    val navigator = rememberAppNavigator()
    val scope = rememberCoroutineScope()
    val tree = remember(session) { TreeState(session, scope) }
    LaunchedEffect(session.account.id) { if (!session.isDemo) container.warmUpImageHosts() }
    var newPage by remember { mutableStateOf<NewPageRequest?>(null) }
    var newDatabase by remember { mutableStateOf(false) }
    var sidebarOpen by rememberSaveable { mutableStateOf(true) }

    fun openLink(url: String) {
        val link = NotionIds.parseLink(url)
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()
        val internal = link != null && (host.isEmpty() || host.contains("notion.") || url.startsWith("notion:"))
        when {
            internal && link.viewId != null -> navigator.navigate(DatabaseRoute(link.id, viewId = link.viewId))
            internal -> navigator.navigate(PageRoute(link.id, blockId = link.blockId))
            else -> context.openUrl(url, settings.openLinksInApp)
        }
    }

    val linkHandler = remember(navigator, session, settings.openLinksInApp) {
        LinkHandler(
            openPage = { navigator.navigate(PageRoute(NotionIds.normalize(it))) },
            openDatabase = { navigator.navigate(DatabaseRoute(NotionIds.normalize(it))) },
            openUrl = ::openLink,
            openUser = { navigator.navigate(PeopleRoute) },
            iconFor = { id -> session.cache.page(id)?.icon ?: session.cache.database(id)?.icon },
        )
    }

    val pending by ShellActions.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val action = pending ?: return@LaunchedEffect
        ShellActions.pending.value = null
        when (action) {
            is ShellAction.OpenLink -> openLink(action.url)
            is ShellAction.ShareText -> {
                val firstLine = action.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                val title = action.subject?.takeIf { it.isNotBlank() } ?: firstLine.take(120)
                val body = if (action.subject.isNullOrBlank() && firstLine == action.text.trim()) "" else action.text
                newPage = NewPageRequest(title = title, body = body, quick = true)
            }
            ShellAction.NewPage -> newPage = NewPageRequest()
            ShellAction.QuickNote -> newPage = NewPageRequest(quick = true)
            ShellAction.NewDatabase -> newDatabase = true
            ShellAction.Search -> navigator.select(Tab.SEARCH)
            ShellAction.Settings -> navigator.navigate(SettingsRoute)
            ShellAction.Back -> navigator.back()
            ShellAction.ToggleSidebar -> sidebarOpen = !sidebarOpen
            ShellAction.ToggleTheme -> container.settingsStore.update { it.copy(themeMode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }
            is ShellAction.SelectTab -> navigator.select(action.tab)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        CompositionLocalProvider(
            LocalSession provides session,
            LocalNavigator provides navigator,
            LocalTree provides tree,
            LocalWideLayout provides expanded,
            LocalLinkHandler provides linkHandler,
        ) {
            if (expanded) {
                ExpandedShell(
                    sidebarOpen = sidebarOpen,
                    onToggleSidebar = { haptics.click(); sidebarOpen = !sidebarOpen },
                    onNewPage = { parent -> newPage = NewPageRequest(parent = parent) },
                ) { paneWidth ->
                    Box(Modifier.fillMaxSize()) {
                        AppNavDisplay(navigator, twoPane = paneWidth >= 900.dp, sidePeek = settings.sidePeek)
                        MessengerHost(Modifier.align(Alignment.BottomCenter))
                    }
                }
            } else {
                SuiteShell(navigator, onNewPage = { newPage = NewPageRequest() }) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavDisplay(navigator, twoPane = false, sidePeek = false)
                        MessengerHost(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }

            newPage?.let { request ->
                NewPageSheet(
                    onDismiss = { newPage = null },
                    onCreated = { page ->
                        request.parent?.let { tree.expand(it) }
                        newPage = null
                        navigator.navigate(PageRoute(page.id))
                    },
                    initialParent = request.parent,
                    quickNote = request.quick,
                    initialTitle = request.title,
                    initialBody = request.body,
                )
            }
            if (newDatabase) NewDatabaseSheet(
                onDismiss = { newDatabase = false },
                onCreated = { db -> newDatabase = false; navigator.navigate(DatabaseRoute(db.id)) },
            )
        }
    }
}

@Composable
private fun SuiteShell(navigator: AppNavigator, onNewPage: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val type = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val isBar = type == NavigationSuiteType.ShortNavigationBarCompact || type == NavigationSuiteType.ShortNavigationBarMedium
    val state = rememberNavigationSuiteScaffoldState()
    val showNavigation = !isBar || navigator.isAtTopLevel
    LaunchedEffect(showNavigation) { if (showNavigation) state.show() else state.hide() }
    NavigationSuiteScaffold(
        navigationItems = {
            Tab.entries.forEach { tab ->
                val selected = navigator.currentTab == tab
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { haptics.segment(); navigator.select(tab) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                    label = { Text(tab.label) },
                    navigationSuiteType = type,
                )
            }
        },
        navigationSuiteType = type,
        state = state,
        containerColor = MaterialTheme.colorScheme.surface,
        primaryActionContent = {
            if (!isBar) {
                FloatingActionButton(
                    onClick = onNewPage,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                ) { Icon(Icons.Rounded.Edit, "New page") }
            }
        },
        content = content,
    )
}

@Composable
private fun ExpandedShell(
    sidebarOpen: Boolean,
    onToggleSidebar: () -> Unit,
    onNewPage: (NodeRef?) -> Unit,
    content: @Composable (paneWidth: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var preferredWidth by rememberSaveable { mutableStateOf(300f) }
    val sidebarWidth by animateDpAsState(
        if (sidebarOpen) preferredWidth.dp else 88.dp,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "sidebar",
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        val paneWidth = maxWidth - sidebarWidth - 8.dp
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
            ) {
                AnimatedContent(sidebarOpen, transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) }, label = "sidebar-content") { open ->
                    if (open) Sidebar(onCollapse = onToggleSidebar, onNewPage = onNewPage)
                    else CollapsedSidebar(onExpand = onToggleSidebar, onNewPage = { onNewPage(null) })
                }
                if (sidebarOpen) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(10.dp)
                            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
                            .draggable(
                                rememberDraggableState { delta -> preferredWidth = (preferredWidth + with(density) { delta.toDp().value }).coerceIn(240f, 440f) },
                                Orientation.Horizontal,
                            ),
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))
                    .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) { content(paneWidth) }
        }
    }
}

@Composable
private fun Sidebar(onCollapse: () -> Unit, onNewPage: (NodeRef?) -> Unit) {
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val tree = LocalTree.current
    val haptics = LocalHaptics.current
    val roots by tree.roots.collectAsState()
    val rootsLoading by tree.rootsLoading.collectAsState()
    val favorites by session.workspace.favorites.collectAsState(emptyList())
    var favoritesOpen by rememberSaveable { mutableStateOf(true) }
    var workspaceOpen by rememberSaveable { mutableStateOf(true) }
    var menuFor by remember { mutableStateOf<NodeRef?>(null) }
    val current = navigator.current
    val selectedId = (current as? PageRoute)?.id ?: (current as? DatabaseRoute)?.id

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceSwitcher(Modifier.weight(1f))
            IconButton(onClick = onCollapse) { Icon(Icons.AutoMirrored.Rounded.MenuOpen, "Collapse sidebar") }
        }
        ExtendedFloatingActionButton(
            onClick = { haptics.click(); onNewPage(null) },
            icon = { Icon(Icons.Rounded.Edit, null) },
            text = { Text("New page") },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        SearchPill(onClick = { navigator.select(Tab.SEARCH) })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                SidebarItem(Tab.HOME.selectedIcon, "Home", selected = current == HomeRoute) { navigator.select(Tab.HOME) }
                SidebarItem(Tab.ACTIVITY.selectedIcon, "Updates", selected = current == ActivityRoute) { navigator.select(Tab.ACTIVITY) }
                SidebarItem(Tab.LIBRARY.selectedIcon, "Library", selected = current == LibraryRoute) { navigator.select(Tab.LIBRARY) }
            }
            item {
                SidebarSection("Favorites", favoritesOpen, onToggle = { favoritesOpen = !favoritesOpen }) {
                    IconButton(onClick = { navigator.openFromSidebar(FavoritesRoute) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Star, "All favorites", Modifier.size(18.dp))
                    }
                }
            }
            if (favoritesOpen) {
                if (favorites.isEmpty()) item {
                    Text(
                        "Star pages to keep them here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 8.dp),
                    )
                }
                items(favorites, key = { "fav-" + it.id }) { ref ->
                    SidebarNode(ref, selected = ref.id == selectedId, onClick = { navigator.openFromSidebar(ref.toRoute()) }, onLongPress = { menuFor = ref })
                }
            }
            item {
                SidebarSection(session.account.displayWorkspace, workspaceOpen, onToggle = { workspaceOpen = !workspaceOpen }) {
                    if (rootsLoading) LoadingIndicator(Modifier.size(28.dp))
                    else IconButton(onClick = { tree.refresh() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Refresh, "Refresh pages", Modifier.size(18.dp)) }
                    IconButton(onClick = { onNewPage(null) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, "New page", Modifier.size(18.dp)) }
                }
            }
            if (workspaceOpen) {
                val list = roots
                if (list == null) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else treeItems(
                    rows = tree.rows(list),
                    selectedId = selectedId,
                    onOpen = { navigator.openFromSidebar(it.toRoute()) },
                    onToggle = { tree.toggle(it) },
                    onAdd = { onNewPage(it) },
                    onLongPress = { menuFor = it },
                    compact = true,
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Column(Modifier.padding(vertical = 6.dp)) {
            SidebarItem(Icons.Rounded.Group, "People", selected = current == PeopleRoute, dense = true) { navigator.openFromSidebar(PeopleRoute) }
            SidebarItem(Icons.Rounded.Delete, "Trash", selected = current == TrashRoute, dense = true) { navigator.openFromSidebar(TrashRoute) }
            SidebarItem(Icons.Rounded.Settings, "Settings", selected = current == SettingsRoute, dense = true) { navigator.openFromSidebar(SettingsRoute) }
        }
    }

    menuFor?.let { ref -> NodeMenuSheet(ref, onDismiss = { menuFor = null }, onAddInside = { onNewPage(ref) }) }
}

@Composable
private fun CollapsedSidebar(onExpand: () -> Unit, onNewPage: () -> Unit) {
    val navigator = LocalNavigator.current
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onExpand) { Icon(Icons.Rounded.Menu, "Expand sidebar") }
        Spacer(Modifier.height(8.dp))
        FloatingActionButton(
            onClick = { haptics.click(); onNewPage() },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
        ) { Icon(Icons.Rounded.Edit, "New page") }
        Spacer(Modifier.height(20.dp))
        Tab.entries.forEach { tab ->
            val selected = navigator.currentTab == tab && navigator.current == tab.root
            RailItem(if (selected) tab.selectedIcon else tab.icon, tab.label, selected) { haptics.segment(); navigator.select(tab) }
        }
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Rounded.Settings, "Settings", navigator.current == SettingsRoute) { navigator.openFromSidebar(SettingsRoute) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val indicator by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "rail")
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 56.dp, height = 32.dp).clip(CircleShape).background(indicator), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceSwitcher(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val accounts by container.accountList.collectAsState()
    var open by remember { mutableStateOf(false) }
    val account = session.account
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { open = true }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    account.displayWorkspace.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "N",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(account.displayWorkspace, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account.displayUser, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.UnfoldMore, "Switch workspace", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            accounts.forEach { other ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(other.displayWorkspace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(other.displayUser, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { UserAvatar(NUser(other.userId ?: other.id, other.userName, other.userAvatar, "person"), 28.dp) },
                    trailingIcon = { if (other.id == account.id) Icon(Icons.Rounded.Check, "Current") },
                    onClick = {
                        open = false
                        if (other.id != account.id) {
                            haptics.confirm()
                            scope.launch { container.switchTo(other.id) }
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Add a workspace") }, leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) }, onClick = { open = false; navigator.openFromSidebar(AccountsRoute) })
            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { open = false; navigator.openFromSidebar(SettingsRoute) })
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(44.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Tab.SEARCH.selectedIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("Search", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            KeyCap("Ctrl P")
        }
    }
}

@Composable
private fun KeyCap(text: String) {
    Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, dense: Boolean = false, onClick: () -> Unit) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "item")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .height(if (dense) 40.dp else 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SidebarSection(title: String, open: Boolean, onToggle: () -> Unit, actions: @Composable () -> Unit) {
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "section")
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp).clip(CircleShape).clickable(onClick = onToggle).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        actions()
        Icon(Icons.Rounded.ExpandMore, if (open) "Collapse $title" else "Expand $title", Modifier.size(20.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SidebarNode(ref: NodeRef, selected: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "node")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(if (selected) 20.dp else 12.dp))
            .background(container)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .height(36.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotionIcon(ref.icon, 20.dp, kind = ref.kind)
        Spacer(Modifier.width(10.dp))
        Text(
            ref.title.ifBlank { "Untitled" },
            style = if (selected) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NodeMenuSheet(ref: NodeRef, onDismiss: () -> Unit, onAddInside: () -> Unit) {
    val session = LocalSession.current
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isFavorite by session.workspace.isFavorite(ref.id).collectAsState(false)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NotionIcon(ref.icon, 32.dp, kind = ref.kind)
            Spacer(Modifier.width(12.dp))
            Text(ref.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val url = ref.url ?: NotionIds.webUrl(ref.id)
        val rows = buildList<Triple<String, ImageVector, () -> Unit>> {
            add(Triple("Open", Icons.AutoMirrored.Rounded.Article) { navigator.openFromSidebar(ref.toRoute()) })
            if (ref.kind == NodeKind.PAGE) add(Triple("Add a page inside", Icons.Rounded.Add, onAddInside))
            add(Triple(if (isFavorite) "Remove from favorites" else "Add to favorites", if (isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star) {
                scope.launch { session.workspace.setFavorite(ref, !isFavorite) }
            })
            add(Triple("Copy link", Icons.Rounded.ContentCopy) { context.copyText(url); messenger.show("Link copied") })
            add(Triple("Open in Notion", Icons.AutoMirrored.Rounded.OpenInNew) { context.openUrl(url, inApp = false) })
        }
        rows.forEach { (label, icon, action) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { Icon(icon, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onDismiss(); action() },
            )
        }
        if (ref.kind == NodeKind.PAGE) ListItem(
            headlineContent = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                onDismiss()
                scope.launch {
                    runCatching { session.pages.trash(ref.id) }
                        .onSuccess { messenger.show("Moved to trash", action = "Undo") { scope.launch { runCatching { session.pages.restore(ref.id) } } } }
                        .onFailure { messenger.error(it) }
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private enum class PaneRole { NONE, LIST, DETAIL }

/** A back stack slot; [occurrence] keeps keys unique when the same destination appears twice. */
private data class Slot(val route: Route, val occurrence: Int, val role: PaneRole) {
    val contentKey: String get() = "$route#$occurrence"
}

private fun Route.isListDestination() = this is SearchRoute || this is LibraryRoute || this is ActivityRoute || this is TrashRoute || this is FavoritesRoute

private fun slotsFor(stack: List<Route>, sidePeek: Boolean): List<Slot> {
    val seen = HashMap<Route, Int>()
    return stack.mapIndexed { index, route ->
        val occurrence = seen.getOrDefault(route, 0)
        seen[route] = occurrence + 1
        val previous = stack.getOrNull(index - 1)
        val next = stack.getOrNull(index + 1)
        val role = when {
            route.isListDestination() -> PaneRole.LIST
            route is DatabaseRoute && sidePeek && next is PageRoute -> PaneRole.LIST
            route is PageRoute && previous != null && (previous.isListDestination() || (sidePeek && previous is DatabaseRoute)) -> PaneRole.DETAIL
            else -> PaneRole.NONE
        }
        Slot(route, occurrence, role)
    }
}

@Composable
private fun AppNavDisplay(navigator: AppNavigator, twoPane: Boolean, sidePeek: Boolean) {
    val settings = LocalAppSettings.current
    val slots = remember(navigator.backStack, sidePeek) { slotsFor(navigator.backStack, sidePeek) }
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val effects = motion.defaultEffectsSpec<Float>()
    val reduce = settings.reduceMotion
    val directive = remember(twoPane) {
        PaneScaffoldDirective(
            maxHorizontalPartitions = if (twoPane) 2 else 1,
            horizontalPartitionSpacerSize = 0.dp,
            maxVerticalPartitions = 1,
            verticalPartitionSpacerSize = 0.dp,
            defaultPanePreferredWidth = 400.dp,
            excludedBounds = emptyList(),
        )
    }
    val listDetail = rememberListDetailSceneStrategy<Slot>(
        directive = directive,
        paneExpansionDragHandle = { state ->
            val interaction = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(state, LocalMinimumInteractiveComponentSize.current, interaction),
                interactionSource = interaction,
            )
        },
    )
    NavDisplay(
        backStack = slots,
        onBack = { navigator.back() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
        transitionSpec = {
            if (reduce) fadeIn(effects) togetherWith fadeOut(effects)
            else (slideInHorizontally(spatial) { it / 5 } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { -it / 10 } + fadeOut(effects))
        },
        popTransitionSpec = {
            if (reduce) fadeIn(effects) togetherWith fadeOut(effects)
            else (slideInHorizontally(spatial) { -it / 10 } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { it / 5 } + fadeOut(effects))
        },
        predictivePopTransitionSpec = {
            fadeIn(effects) + scaleIn(initialScale = 1.04f) togetherWith scaleOut(targetScale = 0.9f) + fadeOut(effects)
        },
        entryProvider = { slot ->
            val metadata = when (slot.role) {
                PaneRole.LIST -> ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })
                PaneRole.DETAIL -> ListDetailSceneStrategy.detailPane()
                PaneRole.NONE -> emptyMap()
            }
            NavEntry(slot, contentKey = slot.contentKey, metadata = metadata) { RouteContent(it.route) }
        },
    )
}

@Composable
private fun DetailPlaceholder() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        EmptyState(
            Icons.AutoMirrored.Rounded.Article,
            "Nothing open",
            "Pick a page from the list to read and edit it here.",
            polygon = MaterialShapes.Cookie12Sided,
        )
    }
}

@Composable
private fun RouteContent(route: Route) {
    when (route) {
        HomeRoute -> HomeScreen()
        SearchRoute -> SearchScreen()
        LibraryRoute -> LibraryScreen()
        ActivityRoute -> ActivityScreen()
        is PageRoute -> PageScreen(route)
        is DatabaseRoute -> DatabaseScreen(route)
        SettingsRoute -> SettingsScreen()
        AppearanceRoute -> AppearanceScreen()
        ReadingRoute -> ReadingScreen()
        AccountsRoute -> AccountsScreen()
        StorageRoute -> StorageScreen()
        AboutRoute -> AboutScreen()
        TrashRoute -> TrashScreen()
        PeopleRoute -> PeopleScreen()
        FavoritesRoute -> FavoritesScreen()
        is MediaRoute -> MediaViewerScreen(route)
    }
}
