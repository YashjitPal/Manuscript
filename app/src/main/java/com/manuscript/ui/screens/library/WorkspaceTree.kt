package com.manuscript.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.manuscript.core.data.ChangeEvent
import com.manuscript.core.data.Session
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.NodeRef
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.theme.LocalHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class TreeRow(val ref: NodeRef, val depth: Int, val expanded: Boolean, val loading: Boolean, val empty: Boolean)

/** Expansion state and lazily loaded children for the workspace page tree. */
@Stable
class TreeState(private val session: Session, private val scope: CoroutineScope) {
    val roots = session.workspace.roots
    val rootsLoading = session.workspace.rootsLoading
    private val expanded = mutableStateMapOf<String, Boolean>()
    private val children = mutableStateMapOf<String, List<NodeRef>>()
    private val loading = mutableStateMapOf<String, Boolean>()

    init {
        scope.launch {
            session.workspace.loadCachedRoots()
            runCatching { session.workspace.refreshRoots() }
        }
        scope.launch {
            session.events.collect { e ->
                when (e) {
                    is ChangeEvent.ChildrenChanged -> if (children.containsKey(e.parentId)) reload(e.parentId)
                    is ChangeEvent.PageCreated, is ChangeEvent.PageTrashed, is ChangeEvent.PageRestored, is ChangeEvent.PageMoved -> {
                        runCatching { session.workspace.refreshRoots(maxPages = 3) }
                        expanded.filterValues { it }.keys.forEach { reload(it) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun isExpanded(id: String) = expanded[id] == true

    fun toggle(ref: NodeRef) {
        if (ref.kind != NodeKind.PAGE) return
        val now = !(expanded[ref.id] ?: false)
        expanded[ref.id] = now
        if (now && !children.containsKey(ref.id)) load(ref)
    }

    fun expand(ref: NodeRef) {
        if (!isExpanded(ref.id)) toggle(ref)
    }

    private fun load(ref: NodeRef) {
        session.workspace.cachedTreeChildren(ref.id)?.let { children[ref.id] = it }
        loading[ref.id] = true
        scope.launch {
            runCatching { session.workspace.treeChildren(ref) }.onSuccess { children[ref.id] = it }
            loading[ref.id] = false
        }
    }

    private fun reload(id: String) {
        val ref = children.values.flatten().firstOrNull { it.id == id }
            ?: roots.value?.firstOrNull { it.id == id }
            ?: NodeRef(id, NodeKind.PAGE, "")
        load(ref)
    }

    fun refresh() {
        scope.launch {
            runCatching { session.workspace.refreshRoots() }
            expanded.filterValues { it }.keys.forEach { reload(it) }
        }
    }

    fun rows(roots: List<NodeRef>): List<TreeRow> {
        val out = mutableListOf<TreeRow>()
        fun visit(ref: NodeRef, depth: Int) {
            val open = expanded[ref.id] == true
            val kids = children[ref.id]
            out += TreeRow(ref, depth, open, loading[ref.id] == true && kids == null, open && kids?.isEmpty() == true)
            if (open && kids != null && depth < 12) kids.forEach { visit(it, depth + 1) }
        }
        roots.forEach { visit(it, 0) }
        return out
    }
}

val LocalTree = staticCompositionLocalOf<TreeState> { error("No TreeState provided") }

fun LazyListScope.treeItems(
    rows: List<TreeRow>,
    selectedId: String?,
    onOpen: (NodeRef) -> Unit,
    onToggle: (NodeRef) -> Unit,
    onAdd: ((NodeRef) -> Unit)?,
    onLongPress: (NodeRef) -> Unit = {},
    compact: Boolean = false,
) {
    items(rows, key = { "tree-" + it.ref.id + "-" + it.depth }) { row ->
        TreeRowItem(row, selected = row.ref.id == selectedId, onOpen, onToggle, onAdd, onLongPress, compact)
        if (row.empty) {
            Text(
                "No pages inside",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = (row.depth + 1) * 18.dp + 44.dp, top = 2.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
fun TreeRowItem(
    row: TreeRow,
    selected: Boolean,
    onOpen: (NodeRef) -> Unit,
    onToggle: (NodeRef) -> Unit,
    onAdd: ((NodeRef) -> Unit)?,
    onLongPress: (NodeRef) -> Unit,
    compact: Boolean,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(if (row.expanded) 90f else 0f, label = "chevron")
    val container = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
    }
    val title = row.ref.title.ifBlank { "Untitled" }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(if (selected) 20.dp else 12.dp))
            .background(container)
            .hoverable(interaction)
            .combinedClickable(
                interactionSource = interaction,
                onClick = { onOpen(row.ref) },
                onLongClick = { haptics.longPress(); onLongPress(row.ref) },
                role = Role.Button,
            )
            .semantics {
                contentDescription = title
                if (row.ref.kind == NodeKind.PAGE) stateDescription = if (row.expanded) "Expanded" else "Collapsed"
            }
            .height(if (compact) 36.dp else 44.dp)
            .padding(start = row.depth * 18.dp + 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .combinedClickable(enabled = row.ref.kind == NodeKind.PAGE, onClick = { haptics.tick(); onToggle(row.ref) }),
            contentAlignment = Alignment.Center,
        ) {
            if (row.loading) LoadingIndicator(Modifier.size(20.dp))
            else if (row.ref.kind == NodeKind.PAGE) Icon(
                Icons.Rounded.ChevronRight,
                if (row.expanded) "Collapse" else "Expand",
                Modifier.size(20.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(2.dp))
        NotionIcon(row.ref.icon, if (compact) 20.dp else 22.dp, kind = row.ref.kind)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = if (selected) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onAdd != null && row.ref.kind == NodeKind.PAGE && (hovered || selected)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).combinedClickable(onClick = { onAdd(row.ref) }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Add a page inside", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}