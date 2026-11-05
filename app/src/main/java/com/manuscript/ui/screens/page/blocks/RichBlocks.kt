package com.manuscript.ui.screens.page.blocks

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.manuscript.ManuscriptApp
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.CodeLanguages
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.RichText
import com.manuscript.core.model.plainText
import com.manuscript.core.net.await
import com.manuscript.core.util.CodeHighlighter
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.NotionImage
import com.manuscript.ui.components.OptionChip
import com.manuscript.ui.components.RichTextView
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.components.isExpiredSignedUrl
import com.manuscript.ui.components.rememberImageRequest
import com.manuscript.ui.screens.page.editor.EditorCallbacks
import com.manuscript.ui.screens.page.editor.MenuKind
import com.manuscript.ui.screens.page.editor.RichEditState
import com.manuscript.ui.screens.page.editor.RichTextEditor
import com.manuscript.ui.screens.page.editor.codeStyles
import com.manuscript.ui.theme.LocalAppSettings
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalReading
import com.manuscript.ui.util.copyText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
private fun Caption(rich: List<RichText>) {
    if (rich.isEmpty()) return
    RichTextView(
        rich,
        LocalReading.current.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
    )
}

fun highlighted(code: String, language: String?, styles: Map<CodeHighlighter.Kind, androidx.compose.ui.text.SpanStyle>): AnnotatedString =
    buildAnnotatedString {
        append(code)
        CodeHighlighter.highlight(code, language).forEach { t -> styles[t.kind]?.let { addStyle(it, t.start, t.end) } }
    }

@Composable
fun CodeBlock(key: String, block: NBlock, scope: BlockScope, editingKey: String?) {
    val reading = LocalReading.current
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val styles = codeStyles()
    val wrapByDefault = LocalAppSettings.current.wrapCode
    var wrap by rememberSaveable(key) { mutableStateOf(wrapByDefault) }
    var picking by remember { mutableStateOf(false) }
    val code = block.plainText
    val language = block.language ?: "plain text"
    Column(Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        language.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = scope.editable) { picking = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { wrap = !wrap }) {
                        Icon(Icons.AutoMirrored.Rounded.WrapText, if (wrap) "Don't wrap" else "Wrap lines", tint = if (wrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { context.copyText(code); haptics.confirm() }) { Icon(Icons.Rounded.ContentCopy, "Copy code") }
                }
                val edit = scope.vm.editState
                val bodyModifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                if (editingKey == key && edit != null) {
                    val focus = remember { FocusRequester() }
                    val target by scope.vm.focus.collectAsState()
                    LaunchedEffect(target) {
                        val t = target
                        if (t != null && t.key == key) {
                            runCatching { focus.requestFocus() }
                            edit.placeCursor(t.cursor)
                            scope.vm.consumeFocus()
                        }
                    }
                    RichTextEditor(edit, reading.code, scope.callbacksFor(key), bodyModifier, placeholder = "Code", code = true, language = language, focusRequester = focus)
                } else {
                    val text = remember(code, language, styles) { highlighted(code, language, styles) }
                    Box(
                        (if (wrap) bodyModifier else bodyModifier.horizontalScroll(rememberScrollState()))
                            .blockGestures(key, scope) { if (scope.editable) scope.vm.startEditing(key) },
                    ) {
                        Text(text.ifEmpty { AnnotatedString(" ") }, style = reading.code, softWrap = wrap, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Caption(block.caption)
    }
    if (picking) {
        LanguagePicker(current = language, onDismiss = { picking = false }) {
            picking = false
            scope.vm.setLanguage(key, it)
        }
    }
}

@Composable
private fun LanguagePicker(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search languages") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(CodeLanguages.filter { it.contains(query.trim(), ignoreCase = true) }) { lang ->
                    ListItem(
                        headlineContent = { Text(lang.replaceFirstChar { it.uppercase() }, fontWeight = if (lang == current) FontWeight.Bold else null) },
                        modifier = Modifier.clickable { onPick(lang) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
fun EquationBlock(key: String, block: NBlock, scope: BlockScope) {
    val expression = block.expression ?: ""
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = LocalDensity.current
    val sizePx = with(density) { (LocalReading.current.body.fontSize.value * 1.25f).sp.toPx() }
    val drawable: Drawable? = remember(expression, color, sizePx) {
        runCatching {
            JLatexMathDrawable.builder(expression).textSize(sizePx).color(color).align(JLatexMathDrawable.ALIGN_CENTER).padding(0).build()
        }.getOrNull()
    }
    var editing by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .blockGestures(key, scope) { if (scope.editable) editing = true }
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null && expression.isNotBlank()) {
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            Canvas(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                drawIntoCanvas { canvas ->
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas.nativeCanvas)
                }
            }
        } else {
            Text(expression.ifBlank { "Empty equation" }, style = LocalReading.current.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) {
        TextEditDialog(
            title = "Equation",
            initial = expression,
            label = "LaTeX",
            monospace = true,
            onDismiss = { editing = false },
        ) { scope.vm.setEquation(key, it); editing = false }
    }
}

@Composable
fun ImageBlock(key: String, block: NBlock, scope: BlockScope) {
    val url = block.file?.url?.takeIf { it.isNotBlank() } ?: block.url ?: return
    val caption = block.caption.plainText()
    var status by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val loaded = status is AsyncImagePainter.State.Success
    val failed = status is AsyncImagePainter.State.Error && !isExpiredSignedUrl(url)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (loaded) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)
                .blockGestures(key, scope) { scope.onOpenMedia(url, "image", caption.ifBlank { null }) },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = rememberImageRequest(url),
                contentDescription = caption.ifBlank { "Image" },
                contentScale = ContentScale.FillWidth,
                onState = { status = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = if (loaded) 0.dp else 200.dp, max = 1400.dp),
            )
            when {
                failed && !block.pending -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.BrokenImage, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Couldn't load this image", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                block.pending || !loaded -> LoadingIndicator(Modifier.size(48.dp))
            }
        }
        Caption(block.caption)
    }
}

private fun youtubeId(url: String): String? =
    Regex("""(?:youtube\.com/(?:watch\?v=|embed/|shorts/)|youtu\.be/)([\w-]{6,})""").find(url)?.groupValues?.get(1)

@Composable
fun MediaBlock(key: String, block: NBlock, scope: BlockScope) {
    val file = block.file
    val url = file?.url?.takeIf { it.isNotBlank() } ?: block.url ?: ""
    val name = file?.displayName ?: url.substringAfterLast('/').substringBefore('?').ifBlank { block.type.label }
    Column(Modifier.fillMaxWidth()) {
        when (block.type) {
            BlockType.AUDIO -> AudioPlayer(url, name, Modifier.blockGestures(key, scope))
            BlockType.VIDEO -> {
                val yt = youtubeId(url)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .blockGestures(key, scope) { if (yt != null || file?.hosted != true && !url.endsWith(".mp4")) scope.onOpenUrl(url) else scope.onOpenMedia(url, "video", name) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (yt != null) NotionImage("https://img.youtube.com/vi/$yt/hqdefault.jpg", Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "Play video", tint = Color.Black, modifier = Modifier.padding(14.dp))
                    }
                    Text(name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                }
            }
            else -> {
                val pdf = block.type == BlockType.PDF || name.endsWith(".pdf", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().blockGestures(key, scope) {
                        if (pdf) scope.onOpenMedia(url, "pdf", name) else scope.onOpenMedia(url, "file", name)
                    },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (pdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (file?.extension?.uppercase()?.ifBlank { null } ?: if (pdf) "PDF" else "File") + if (file?.hosted == true) " · Uploaded to Notion" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (block.pending) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        Caption(block.caption)
    }
}

@Composable
fun AudioPlayer(url: String, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    DisposableEffect(url) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let {
                position = it.currentPosition
                duration = it.duration.coerceAtLeast(0)
                progress = if (duration > 0) position.toFloat() / duration else 0f
            }
            delay(200)
        }
    }
    fun fmt(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = {
                    val p = player ?: ExoPlayer.Builder(context).build().also { exo ->
                        exo.setMediaItem(MediaItem.fromUri(url))
                        exo.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                        })
                        exo.prepare()
                        player = exo
                    }
                    if (p.isPlaying) p.pause() else p.play()
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(52.dp),
            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Audiotrack, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                if (playing) LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (duration > 0) "${fmt(position)} / ${fmt(duration)}" else "Tap play to stream",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

data class LinkPreview(val title: String?, val description: String?, val image: String?, val site: String?, val favicon: String?)

object LinkPreviews {
    private val cache = android.util.LruCache<String, LinkPreview>(200)

    suspend fun load(url: String): LinkPreview? {
        cache.get(url)?.let { return it }
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: return null
        val favicon = "https://www.google.com/s2/favicons?domain=$host&sz=64"
        val preview = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android) Manuscript/1.0").build()
                ManuscriptApp.container.httpClient.newCall(request).await().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body.source().let { source ->
                        source.request(256 * 1024)
                        source.buffer.snapshot().utf8()
                    }
                    fun meta(vararg names: String): String? = names.firstNotNullOfOrNull { n ->
                        Regex("""<meta[^>]+(?:property|name)=["']$n["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                            ?: Regex("""<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']$n["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                    }?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
                    val title = meta("og:title", "twitter:title") ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
                    val image = meta("og:image", "twitter:image")?.let { img -> if (img.startsWith("//")) "https:$img" else if (img.startsWith("/")) "https://$host$img" else img }
                    LinkPreview(title, meta("og:description", "description", "twitter:description"), image, meta("og:site_name") ?: host, favicon)
                }
            }.getOrNull()
        } ?: LinkPreview(null, null, null, host, favicon)
        cache.put(url, preview)
        return preview
    }
}

@Composable
fun LinkBlock(key: String, block: NBlock, scope: BlockScope) {
    val url = block.url ?: block.file?.url ?: return
    val preview by produceState<LinkPreview?>(null, url) { value = LinkPreviews.load(url) }
    Column(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).blockGestures(key, scope) { scope.onOpenUrl(url) },
        ) {
            BoxWithConstraints {
                val side = maxWidth > 480.dp
                val image = preview?.image
                Row {
                    Column(Modifier.weight(1f).padding(16.dp)) {
                        Text(
                            preview?.title ?: block.caption.plainText().ifBlank { url },
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        preview?.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            preview?.favicon?.let { NotionIcon(NIcon.External(it), 16.dp) } ?: Icon(Icons.Rounded.Language, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(url, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (image != null && side) {
                        NotionImage(image, Modifier.width(180.dp).heightIn(min = 120.dp, max = 140.dp))
                    }
                }
            }
        }
        Caption(block.caption)
    }
}

@Composable
fun TableBlock(key: String, block: NBlock, scope: BlockScope) {
    val rows = block.children.filter { it.type == BlockType.TABLE_ROW }
    val width = block.tableWidth.coerceAtLeast(1)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val header = MaterialTheme.colorScheme.surfaceContainerHigh
    var editingCell by remember { mutableStateOf<Pair<String, Int>?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth().blockGestures(key, scope)) {
        val cellWidth = (maxWidth / width).coerceIn(120.dp, 260.dp)
        Column {
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, outline, RoundedCornerShape(14.dp))) {
                    rows.forEachIndexed { r, row ->
                        val rowKey = scope.vm.keyFor(row.id)
                        Row {
                            (0 until width).forEach { c ->
                                val isHeader = (r == 0 && block.hasColumnHeader) || (c == 0 && block.hasRowHeader)
                                Box(
                                    Modifier
                                        .width(cellWidth)
                                        .heightIn(min = 44.dp)
                                        .background(if (isHeader) header else Color.Transparent)
                                        .border(0.5.dp, outline)
                                        .clickable(enabled = scope.editable) { editingCell = rowKey to c }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    RichTextView(
                                        row.cells.getOrNull(c) ?: emptyList(),
                                        LocalReading.current.body.copy(fontWeight = if (isHeader) FontWeight.SemiBold else null, fontSize = LocalReading.current.body.fontSize * 0.92f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (scope.editable) {
                TextButton(onClick = { scope.vm.addTableRow(key) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New row")
                }
            }
        }
    }
    editingCell?.let { (rowKey, column) ->
        val row = scope.vm.blockByKey(rowKey)
        RichEditDialog(
            title = "Edit cell",
            initial = row?.cells?.getOrNull(column) ?: emptyList(),
            onDismiss = { editingCell = null },
        ) {
            scope.vm.setCell(rowKey, column, it)
            editingCell = null
        }
    }
}

@Composable
fun ColumnsBlock(block: NBlock, scope: BlockScope, editingKey: String?) {
    val columns = block.children.filter { it.type == BlockType.COLUMN }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 560.dp || columns.size <= 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                columns.forEach { BlockList(it.children, scope, editingKey) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                columns.forEach { col ->
                    Box(Modifier.weight(col.widthRatio?.takeIf { it > 0f } ?: (1f / columns.size))) { BlockList(col.children, scope, editingKey) }
                }
            }
        }
    }
}

@Composable
fun TabsBlock(key: String, block: NBlock, scope: BlockScope, editingKey: String?) {
    val tabs = block.children
    var selectedRaw by rememberSaveable(key) { mutableIntStateOf(0) }
    val selected = selectedRaw.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, tab ->
                FilterChip(
                    selected = i == selected,
                    onClick = { selectedRaw = i; haptics.segment() },
                    label = { Text(tab.plainText.ifBlank { "Tab ${i + 1}" }) },
                    leadingIcon = tab.icon?.let { { NotionIcon(it, 18.dp) } },
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
        tabs.getOrNull(selected)?.let { tab ->
            Box(Modifier.padding(top = 10.dp)) { BlockList(tab.children, scope, editingKey) }
        }
    }
}

private data class InlinePreview(val title: String, val icon: NIcon?, val rows: List<NPage>, val dataSourceId: String?)

@Composable
fun InlineDatabase(key: String, block: NBlock, scope: BlockScope) {
    val session = LocalSession.current
    val preview by produceState<InlinePreview?>(null, block.id) {
        value = runCatching {
            val (db, sources) = session.databases.resolve(block.id)
            val ds = sources.firstOrNull()
            val rows = ds?.let { session.databases.query(it.id, null, null, pageSize = 6).items } ?: emptyList()
            InlinePreview(db.titleText.ifBlank { block.title ?: "Untitled" }, db.icon ?: ds?.icon, rows, ds?.id)
        }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().blockGestures(key, scope) { scope.onOpenDatabase(block.id) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotionIcon(preview?.icon, 26.dp, kind = NodeKind.DATABASE, fallback = Icons.Rounded.TableChart)
                Spacer(Modifier.width(10.dp))
                Text(preview?.title ?: block.title ?: "Database", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { scope.onOpenDatabase(block.id) }, shapes = ButtonDefaults.shapes()) { Text("Open") }
            }
            when {
                preview == null -> LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                preview!!.rows.isEmpty() -> Text("No entries yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
                else -> Column(Modifier.padding(bottom = 8.dp)) {
                    preview!!.rows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().clickable { scope.onOpenPage(row.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NotionIcon(row.icon, 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            row.properties.values.firstNotNullOfOrNull { v ->
                                when (v) {
                                    is PropValue.Status -> v.option
                                    is PropValue.Select -> v.option
                                    else -> null
                                }
                            }?.let { OptionChip(it, small = true) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeetingNotesBlock(key: String, block: NBlock, scope: BlockScope, editingKey: String?) {
    val meeting = block.meeting
    val users by scope.vm.users.collectAsState()
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().blockGestures(key, scope)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(block.title ?: "Meeting notes", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                meeting?.status?.let { Text(it.replace('_', ' '), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
            meeting?.startTime?.let { start ->
                Text(
                    Fmt.dateTime(start) + (meeting.endTime?.let { " – " + Fmt.dateTime(it).substringAfter(" at ") } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (meeting?.attendees?.isNotEmpty() == true) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    meeting.attendees.take(8).forEach { id -> UserAvatar(users[id], 28.dp, ring = MaterialTheme.colorScheme.tertiaryContainer) }
                }
            }
            if (block.children.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                BlockList(block.children, scope, editingKey)
            }
        }
    }
}

@Composable
fun TextEditDialog(
    title: String,
    initial: String,
    label: String,
    monospace: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                textStyle = if (monospace) LocalReading.current.code else MaterialTheme.typography.bodyLarge,
                minLines = if (monospace) 3 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private object NoopCallbacks : EditorCallbacks {
    override fun onSplit(right: List<RichText>) = Unit
    override fun onBackspaceAtStart() = Unit
    override fun onShortcut(type: BlockType) = Unit
    override fun onFocusPrevious() = Unit
    override fun onFocusNext() = Unit
    override fun onIndent() = Unit
    override fun onOutdent() = Unit
    override fun onMenu(kind: MenuKind?, start: Int, query: String) = Unit
    override fun onChanged() = Unit
    override fun onDone() = Unit
    override fun onLinkRequest() = Unit
    override fun onFocusLost() = Unit
}

/** A dialog for editing a short piece of rich text, keeping its formatting. */
@Composable
fun RichEditDialog(title: String, initial: List<RichText>, onDismiss: () -> Unit, onSave: (List<RichText>) -> Unit) {
    val state = remember(initial) { RichEditState(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                RichTextEditor(state, LocalReading.current.body, NoopCallbacks, Modifier.fillMaxWidth().padding(14.dp), placeholder = "Empty", focusRequester = focus)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(state.toRichText()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}