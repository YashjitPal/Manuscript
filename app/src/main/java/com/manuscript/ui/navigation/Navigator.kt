package com.manuscript.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.manuscript.core.json.NotionJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
sealed interface Route : NavKey

@Serializable data object HomeRoute : Route
@Serializable data object SearchRoute : Route
@Serializable data object LibraryRoute : Route
@Serializable data object ActivityRoute : Route
@Serializable data class PageRoute(val id: String, val blockId: String? = null, val full: Boolean = false) : Route
@Serializable data class DatabaseRoute(val id: String, val dataSourceId: String? = null, val viewId: String? = null) : Route
@Serializable data object SettingsRoute : Route
@Serializable data object AppearanceRoute : Route
@Serializable data object ReadingRoute : Route
@Serializable data object AccountsRoute : Route
@Serializable data object StorageRoute : Route
@Serializable data object AboutRoute : Route
@Serializable data object TrashRoute : Route
@Serializable data object PeopleRoute : Route
@Serializable data object FavoritesRoute : Route
@Serializable data class MediaRoute(val url: String, val kind: String, val name: String? = null) : Route

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val root: Route) {
    HOME("Home", Icons.Outlined.Home, Icons.Rounded.Home, HomeRoute),
    SEARCH("Search", Icons.Outlined.Search, Icons.Rounded.Search, SearchRoute),
    LIBRARY("Library", Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard, LibraryRoute),
    ACTIVITY("Updates", Icons.Outlined.Bolt, Icons.Rounded.Bolt, ActivityRoute),
}

/** Keeps one back stack per top-level tab; Home is always underneath the others. */
@Stable
class AppNavigator(initialStacks: Map<Tab, List<Route>>, initialTab: Tab) {
    private val stacks: Map<Tab, SnapshotStateList<Route>> = Tab.entries.associateWith { tab ->
        mutableStateListOf<Route>().apply { addAll(initialStacks[tab]?.takeIf { it.isNotEmpty() } ?: listOf(tab.root)) }
    }

    var currentTab by mutableStateOf(initialTab)
        private set

    val backStack: List<Route> by derivedStateOf {
        val home = stacks.getValue(Tab.HOME)
        if (currentTab == Tab.HOME) home.toList() else home.take(1) + stacks.getValue(currentTab)
    }

    val current: Route get() = backStack.last()
    val isAtTopLevel: Boolean get() = stacks.getValue(currentTab).size == 1

    fun stack(tab: Tab): List<Route> = stacks.getValue(tab)

    fun navigate(route: Route) {
        val stack = stacks.getValue(currentTab)
        if (stack.lastOrNull() == route) return
        if (route is PageRoute && stack.lastOrNull() is PageRoute && (stack.last() as PageRoute).id == route.id) {
            stack[stack.lastIndex] = route
            return
        }
        stack.add(route)
        while (stack.size > 40) stack.removeAt(1)
    }

    fun replace(route: Route) {
        val stack = stacks.getValue(currentTab)
        if (stack.size > 1) stack[stack.lastIndex] = route else stack.add(route)
    }

    /** Opens a destination from the sidebar: it becomes the new top of the Home stack. */
    fun openFromSidebar(route: Route) {
        currentTab = Tab.HOME
        val stack = stacks.getValue(Tab.HOME)
        if (stack.lastOrNull() == route) return
        stack.add(route)
        while (stack.size > 40) stack.removeAt(1)
    }

    fun select(tab: Tab) {
        if (tab == currentTab) popToRoot(tab) else currentTab = tab
    }

    fun popToRoot(tab: Tab = currentTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun back(): Boolean {
        val stack = stacks.getValue(currentTab)
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (currentTab != Tab.HOME) {
            currentTab = Tab.HOME
            return true
        }
        return false
    }

    fun backTo(predicate: (Route) -> Boolean) {
        val stack = stacks.getValue(currentTab)
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }

    companion object {
        private val serializer = MapSerializer(String.serializer(), ListSerializer(Route.serializer()))

        val Saver: Saver<AppNavigator, String> = Saver(
            save = { nav ->
                val stacks = Tab.entries.associate { it.name to nav.stack(it).toList() } + ("__tab" to listOf(nav.currentTab.root))
                NotionJson.encodeToString(serializer, stacks)
            },
            restore = { saved ->
                runCatching {
                    val map = NotionJson.decodeFromString(serializer, saved)
                    val tabRoot = map["__tab"]?.firstOrNull()
                    AppNavigator(
                        Tab.entries.associateWith { map[it.name] ?: listOf(it.root) },
                        Tab.entries.firstOrNull { it.root == tabRoot } ?: Tab.HOME,
                    )
                }.getOrNull()
            },
        )
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) {
    AppNavigator(emptyMap(), Tab.HOME)
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("No AppNavigator provided") }
