package com.manuscript.ui.screens.database.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.ShapeBadge
import com.manuscript.ui.screens.database.DbUi
import com.manuscript.ui.screens.database.value
import com.manuscript.ui.screens.properties.PropertyEditor
import com.manuscript.ui.screens.properties.PropertyRow
import com.manuscript.ui.screens.properties.PropertyValueView
import com.manuscript.ui.screens.properties.emptyValueFor
import com.manuscript.ui.screens.properties.isEmpty
import com.manuscript.ui.theme.LocalHaptics

@Composable
fun ListView(state: DbUi, actions: ViewActions, modifier: Modifier = Modifier) {
    val props = state.visibleProps().filter { it.type != PropType.TITLE }
    val listState = rememberLazyListState()
    val haptics = LocalHaptics.current
    LoadMoreOnEnd(listState, state.cursor != null) { actions.loadMore() }
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
        items(state.rows, key = { it.id }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .combinedClickable(onClick = { actions.open(row) }, onLongClick = { haptics.longPress(); actions.rowMenu(row) })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotionIcon(row.icon, 22.dp, kind = NodeKind.PAGE)
                Spacer(Modifier.width(12.dp))
                Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 420.dp)) {
                    props.take(3).forEach { p ->
                        val v = row.value(p)
                        if (v != null && !v.isEmpty()) PropertyValueView(v, p, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        if (actions.editable) item { NewRowButton { actions.create(emptyMap()) } }
    }
}

@Composable
fun GalleryView(state: DbUi, actions: ViewActions, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val config = state.config
    val props = state.visibleProps().filter { it.type != PropType.TITLE }
    val min = when (config.coverSize) { "small" -> 170.dp; "large" -> 300.dp; else -> 220.dp }
    val coverHeight = when (config.coverSize) { "small" -> 110.dp; "large" -> 220.dp; else -> 150.dp }
    val gridState = rememberLazyGridState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1.5f, behindFraction = 0.5f))
    androidx.compose.runtime.LaunchedEffect(gridState, state.cursor != null) {
        if (state.cursor == null) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (it >= gridState.layoutInfo.totalItemsCount - 5) actions.loadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(min),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.rows, key = { it.id }) { row ->
            RowCard(
                row, props,
                coverFor(row, config.coverType, config.coverPropertyId, ds) ?: if (config.coverType == null) row.cover else null,
                coverHeight = coverHeight,
                coverFit = config.coverAspect == "contain",
                actions = actions,
                compact = config.cardLayout == "compact",
                modifier = Modifier.animateItem(),
            )
        }
        if (actions.editable) item(span = { GridItemSpan(maxLineSpan) }) { NewRowButton { actions.create(emptyMap()) } }
    }
}

/** Notion forms: one field per editable property; submitting creates a new entry. */
@Composable
fun FormView(state: DbUi, actions: ViewActions, onSubmit: (Map<String, PropValue>) -> Unit, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val fields = (listOfNotNull(ds.titleProperty) + state.visibleProps().filter { it.type != PropType.TITLE })
        .distinctBy { it.id }.filter { it.type.editable }
    val values = remember(ds.id) { mutableStateMapOf<String, PropValue>() }
    var editing by remember { mutableStateOf<PropertySchema?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    if (state.config.formClosed == true) {
        EmptyState(Icons.Rounded.Lock, "This form is closed", "It isn't accepting new responses right now.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp)) {
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.view?.name ?: "Form", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text("Fill this in to add a new entry to ${ds.titleText.ifBlank { "this database" }}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    fields.forEach { field -> PropertyRow(field, values[field.name], onClick = { editing = field }) }
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = {
                            haptics.confirm()
                            onSubmit(values.toMap())
                            values.clear()
                            submitted = true
                        },
                        enabled = actions.editable,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Submit") }
                    if (submitted) Text("Thanks — your response was added.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    editing?.let { field ->
        PropertyEditor(field, values[field.name] ?: emptyValueFor(field), onDismiss = { editing = null }) { v -> values[field.name] = v }
    }
}

@Composable
fun MapView(state: DbUi, actions: ViewActions, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val context = LocalContext.current
    val placeProp = ds.prop(state.config.mapBy) ?: ds.properties.firstOrNull { it.type == PropType.PLACE }
    val located = state.rows.mapNotNull { row -> (placeProp?.let { row.value(it) } as? PropValue.Place)?.let { row to it } }
    if (placeProp == null || located.isEmpty()) {
        EmptyState(Icons.Rounded.Map, "No places yet", "Entries with a place property appear here.", modifier, polygon = MaterialShapes.Pentagon)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(located, key = { it.first.id }) { (row, place) ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { actions.open(row) }, onLongClick = {
                    val uri = if (place.lat != null && place.lon != null) Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name ?: row.title)})")
                    else Uri.parse("geo:0,0?q=${Uri.encode(place.address ?: place.name ?: row.title)}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.Place, polygon = MaterialShapes.Pentagon, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(place.name, place.address).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Long-press to open in Maps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun UnsupportedView(name: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        Text("$name views can't be displayed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}