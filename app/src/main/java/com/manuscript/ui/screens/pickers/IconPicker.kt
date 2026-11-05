package com.manuscript.ui.screens.pickers

import android.content.Context
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NCustomEmoji
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NIcon
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.NotionImage
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EmojiData {
    data class Emoji(val char: String, val name: String)
    data class Group(val name: String, val emojis: List<Emoji>)

    @Volatile private var cached: List<Group>? = null
    @Volatile private var icons: List<String>? = null

    private val shortNames = mapOf(
        "Smileys & Emotion" to "Smileys", "People & Body" to "People", "Animals & Nature" to "Nature", "Food & Drink" to "Food",
        "Travel & Places" to "Travel", "Activities" to "Activities", "Objects" to "Objects", "Symbols" to "Symbols", "Flags" to "Flags",
    )

    suspend fun groups(context: Context): List<Group> = cached ?: withContext(Dispatchers.IO) {
        val paint = Paint()
        val result = mutableListOf<Group>()
        var name = ""
        var current = mutableListOf<Emoji>()
        context.assets.open("emoji.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trimEnd('\r', '\n')
                if (line.startsWith("#")) {
                    if (current.isNotEmpty()) result += Group(shortNames[name] ?: name, current)
                    name = line.removePrefix("#")
                    current = mutableListOf()
                } else if (line.isNotBlank()) {
                    val parts = line.split('\t')
                    val emoji = parts[0]
                    if (paint.hasGlyph(emoji)) current += Emoji(emoji, parts.getOrElse(1) { "" })
                }
            }
        }
        if (current.isNotEmpty()) result += Group(shortNames[name] ?: name, current)
        result.also { cached = it }
    }

    suspend fun nativeIcons(context: Context): List<String> = icons ?: withContext(Dispatchers.IO) {
        context.assets.open("notion_icons.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }.also { icons = it }
    }
}

private enum class IconTab(val label: String) { EMOJI("Emoji"), ICONS("Icons"), CUSTOM("Custom"), UPLOAD("Upload") }

@Composable
fun IconPickerSheet(
    onDismiss: () -> Unit,
    onPick: (NIcon?) -> Unit,
    onUpload: ((Uri) -> Unit)? = null,
    title: String = "Icon",
) {
    val context = LocalContext.current
    val session = LocalSession.current
    val haptics = LocalHaptics.current
    val palette = LocalNotionPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var hue by rememberSaveable { mutableStateOf(Hue.GRAY) }
    var link by remember { mutableStateOf("") }
    val groups by produceState<List<EmojiData.Group>?>(null) { value = EmojiData.groups(context) }
    val nativeIcons by produceState(emptyList<String>()) { value = EmojiData.nativeIcons(context) }
    val custom by produceState<List<NCustomEmoji>?>(null) {
        value = runCatching { session.api.customEmojis().items }.getOrDefault(emptyList())
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && onUpload != null) { onUpload(uri); onDismiss() }
    }
    val tabs = IconTab.entries

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding()) {
            Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    haptics.confirm()
                    groups?.flatMap { it.emojis }?.randomOrNull()?.let { onPick(NIcon.Emoji(it.char)); onDismiss() }
                }) { Icon(Icons.Rounded.Casino, "Random emoji") }
                TextButton(onClick = { onPick(null); onDismiss() }) { Text("Remove") }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) }) }
            }
            when (tabs[tab]) {
                IconTab.EMOJI -> Column {
                    SearchField(query, "Search emoji") { query = it }
                    val list = groups
                    if (list == null) Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    else LazyVerticalGrid(
                        GridCells.Adaptive(48.dp),
                        Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 12.dp),
                    ) {
                        val q = query.trim().lowercase()
                        list.forEach { group ->
                            val emojis = if (q.isEmpty()) group.emojis else group.emojis.filter { it.name.contains(q) }
                            if (emojis.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "h-" + group.name) {
                                    Text(group.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
                                }
                                items(emojis, key = { group.name + it.char }) { e ->
                                    Box(
                                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable { haptics.tick(); onPick(NIcon.Emoji(e.char)); onDismiss() },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(e.char, style = MaterialTheme.typography.headlineSmall) }
                                }
                            }
                        }
                    }
                }
                IconTab.ICONS -> Column {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Hue.iconHues.forEach { h ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent(h))
                                    .border(3.dp, if (h == hue) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                    .clickable { hue = h; haptics.segment() },
                            )
                        }
                    }
                    SearchField(query, "Search icons") { query = it }
                    LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 12.dp)) {
                        items(nativeIcons.filter { it.contains(query.trim().replace(' ', '-'), ignoreCase = true) }, key = { it }) { name ->
                            val icon = NIcon.Native(name, hue.api)
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable { haptics.tick(); onPick(icon); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) { NotionIcon(icon, 30.dp) }
                        }
                    }
                }
                IconTab.CUSTOM -> {
                    val list = custom
                    when {
                        list == null -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        list.isEmpty() -> Text(
                            "This workspace has no custom emoji.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                        else -> LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(12.dp)) {
                            items(list, key = { it.id }) { e ->
                                Column(
                                    Modifier.clip(RoundedCornerShape(14.dp)).clickable { onPick(NIcon.CustomEmoji(e.id, e.name, e.url)); onDismiss() }.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    NotionIcon(NIcon.CustomEmoji(e.id, e.name, e.url), 36.dp)
                                    Text(e.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                IconTab.UPLOAD -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (onUpload != null) {
                        FilledTonalButton(
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Upload an image")
                        }
                        Text("Recommended size 280 × 280 px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Image link") },
                        leadingIcon = { Icon(Icons.Rounded.Link, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onPick(NIcon.External(link.trim())); onDismiss() },
                        enabled = link.startsWith("http"),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Use link") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear") } },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private const val COVERS = "https://www.notion.so/images/page-cover/"

val CoverGallery: List<Pair<String, List<String>>> = listOf(
    "Color & gradient" to (listOf("solid_red.png", "solid_yellow.png", "solid_blue.png", "solid_beige.png") + (1..9).map { "gradients_$it.png" } + listOf("gradients_10.jpg", "gradients_11.jpg")),
    "James Webb Telescope" to (1..4).map { "webb$it.jpg" },
    "NASA Archive" to listOf(
        "nasa_carina_nebula.jpg", "nasa_orion_nebula.jpg", "nasa_the_blue_marble.jpg", "nasa_earth_grid.jpg", "nasa_buzz_aldrin_on_the_moon.jpg",
        "nasa_eagle_in_lunar_orbit.jpg", "nasa_great_sandy_desert_australia.jpg", "nasa_fingerprints_of_water_on_the_sand.jpg",
        "nasa_robert_stewart_spacewalk.jpg", "nasa_bruce_mccandless_spacewalk.jpg", "nasa_tim_peake_spacewalk.jpg",
        "nasa_space_shuttle_columbia.jpg", "nasa_space_shuttle_challenger.jpg", "nasa_wrights_first_flight.jpg",
        "nasa_new_york_city_grid.jpg", "nasa_ibm_type_704.jpg", "nasa_multi-axis_gimbal_rig.jpg", "nasa_reduced_gravity_walking_simulator.jpg",
    ),
    "Japanese prints" to ((1..11) + (13..16)).map { "woodcuts_$it.jpg" },
    "The Met Museum" to listOf(
        "met_william_morris_1875.jpg", "met_william_morris_1877_willow.jpg", "met_william_morris_1878.jpg", "met_georges_seurat_1884.jpg",
        "met_vincent_van_gogh_cradle.jpg", "met_vincent_van_gogh_oleanders.jpg", "met_vincent_van_gogh_ginoux.jpg",
        "met_frederic_edwin_church_1871.jpg", "met_the_unicorn_in_captivity.jpg", "met_emanuel_leutze.jpg", "met_terracotta_funerary_plaque.jpg",
        "met_paul_signac.jpg", "met_henri_rousseau_1907.jpg", "met_horace_pippin.jpg", "met_joseph_hidley_1870.jpg", "met_arnold_bocklin_1880.jpg",
        "met_bruegel_1565.jpg", "met_canaletto_1720.jpg", "met_fitz_henry_lane.jpg", "met_jules_tavernier_1878.jpg", "met_winslow_homer_maine_coast.jpg",
    ),
    "Rijksmuseum" to listOf(
        "rijksmuseum_vermeer_the_milkmaid.jpg", "rijksmuseum_rembrandt_1642.jpg", "rijksmuseum_jansz_1636.jpg", "rijksmuseum_jansz_1637.jpg",
        "rijksmuseum_jansz_1641.jpg", "rijksmuseum_jansz_1649.jpg", "rijksmuseum_avercamp_1608.jpg", "rijksmuseum_avercamp_1620.jpg",
        "rijksmuseum_claesz_1628.jpg", "rijksmuseum_mignons_1660.jpg",
    ),
).map { (name, files) -> name to files.map { COVERS + it } }

@Composable
fun CoverPickerSheet(onDismiss: () -> Unit, onPick: (NFile?) -> Unit, onUpload: (Uri) -> Unit) {
    var link by remember { mutableStateOf("") }
    val haptics = LocalHaptics.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { onUpload(uri); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding()) {
            Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cover", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    haptics.confirm()
                    CoverGallery.flatMap { it.second }.randomOrNull()?.let { onPick(NFile(it)); onDismiss() }
                }) { Icon(Icons.Rounded.Casino, "Random cover") }
                TextButton(onClick = { onPick(null); onDismiss() }) { Text("Remove") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Image, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload")
                }
                OutlinedTextField(
                    value = link, onValueChange = { link = it }, placeholder = { Text("or paste an image link") }, singleLine = true,
                    shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f),
                    trailingIcon = {
                        if (link.startsWith("http")) TextButton(onClick = { onPick(NFile(link.trim())); onDismiss() }) { Text("Use") }
                    },
                )
            }
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 12.dp)) {
                CoverGallery.forEach { (name, urls) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-$name") {
                        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp))
                    }
                    items(urls, key = { it }) { url ->
                        Box(
                            Modifier
                                .padding(4.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { haptics.tick(); onPick(NFile(url)); onDismiss() },
                        ) { NotionImage(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
