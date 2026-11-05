package com.manuscript.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manuscript.core.AppContainer
import com.manuscript.core.data.Session
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.ui.navigation.AppNavigator
import com.manuscript.ui.navigation.DatabaseRoute
import com.manuscript.ui.navigation.PageRoute

val LocalSession = staticCompositionLocalOf<Session> { error("No Session provided") }
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

/** Whether the app is laid out for a large screen (persistent sidebar, side-by-side panes). */
val LocalWideLayout = staticCompositionLocalOf { false }

fun AppNavigator.open(ref: NodeRef) = when (ref.kind) {
    NodeKind.PAGE -> navigate(PageRoute(ref.id))
    NodeKind.DATABASE -> navigate(DatabaseRoute(ref.id))
    NodeKind.DATA_SOURCE -> navigate(DatabaseRoute(ref.databaseId ?: ref.id, dataSourceId = ref.id))
}

fun NodeRef.toRoute() = when (kind) {
    NodeKind.PAGE -> PageRoute(id)
    NodeKind.DATABASE -> DatabaseRoute(id)
    NodeKind.DATA_SOURCE -> DatabaseRoute(databaseId ?: id, dataSourceId = id)
}

/** Creates a ViewModel bound to the current session, scoped to the current navigation entry. */
@Composable
inline fun <reified VM : ViewModel> sessionViewModel(key: String? = null, crossinline create: (Session) -> VM): VM {
    val session = LocalSession.current
    return viewModel(key = key?.let { "${session.account.id}:$it" } ?: session.account.id) { create(session) }
}
