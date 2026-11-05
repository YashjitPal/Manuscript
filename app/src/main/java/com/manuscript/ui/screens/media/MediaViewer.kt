package com.manuscript.ui.screens.media

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalContainer
import com.manuscript.ui.components.LocalMessenger
import com.manuscript.ui.components.NotionImage
import com.manuscript.ui.components.ShapeBadge
import com.manuscript.ui.navigation.LocalNavigator
import com.manuscript.ui.navigation.MediaRoute
import com.manuscript.ui.util.openUrl
import com.manuscript.ui.util.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private fun fileName(route: MediaRoute): String =
    route.name?.takeIf { it.isNotBlank() }
        ?: route.url.substringBefore('?').substringAfterLast('/').let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }.ifBlank { "file" }

private fun mimeOf(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

private fun Context.download(url: String, name: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        .setMimeType(mimeOf(name))
    getSystemService(DownloadManager::class.java)?.enqueue(request)
}

private suspend fun fetchToCache(client: OkHttpClient, context: Context, url: String, name: String): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "media").apply { mkdirs() }
    val file = File(dir, "${url.substringBefore('?').hashCode().toUInt()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
    if (file.exists() && file.length() > 0) return@withContext file
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("Download failed (${response.code})")
        val tmp = File(dir, file.name + ".part")
        response.body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.renameTo(file)
    }
    file
}

@Composable
fun MediaViewerScreen(route: MediaRoute) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val name = remember(route) { fileName(route) }
    val immersive = route.kind == "image" || route.kind == "video"
    var chrome by remember { mutableStateOf(true) }
    val content = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = if (immersive) Color.Black else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            when (route.kind) {
                "image" -> ZoomableImage(route.url, onTap = { chrome = !chrome })
                "video" -> VideoPlayer(route.url, chrome, onTap = { chrome = !chrome })
                "pdf" -> PdfViewer(route.url, name)
                else -> FileCard(route.url, name)
            }
            AnimatedVisibility(chrome || !immersive, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { context.shareText(route.url, name) }) { Icon(Icons.Rounded.Share, "Share link") }
                        IconButton(onClick = { context.download(route.url, name); messenger.show("Downloading $name") }) { Icon(Icons.Rounded.Download, "Download") }
                        IconButton(onClick = { context.openUrl(route.url, inApp = false) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in browser") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (immersive) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                        titleContentColor = content,
                        navigationIconContentColor = content,
                        actionIconContentColor = content,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                launch { scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f)) }
                                offset.animateTo(Offset.Zero, spring(dampingRatio = 0.8f, stiffness = 380f))
                            } else {
                                val target = 2.75f
                                val center = Offset(this@pointerInput.size.width / 2f, this@pointerInput.size.height / 2f)
                                launch { scale.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 380f)) }
                                offset.animateTo(clamp((center - tap) * (target - 1f), target), spring(dampingRatio = 0.8f, stiffness = 380f))
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scope.launch {
                        val s = (scale.value * zoom).coerceIn(1f, 6f)
                        scale.snapTo(s)
                        offset.snapTo(if (s <= 1f) Offset.Zero else clamp(offset.value + pan, s))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        NotionImage(
            url,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                },
            contentScale = ContentScale.Fit,
            placeholderColor = Color.Black,
        )
    }
}

@Composable
private fun VideoPlayer(url: String, showControls: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val playPause = rememberPlayPauseButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, 250L)

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                val duration = progress.durationMs.coerceAtLeast(1L)
                var scrubbing by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = scrubbing ?: (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = { scrubbing?.let { player.seekTo((it * duration).toLong()) }; scrubbing = null },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = playPause::onClick,
                        enabled = playPause.isEnabled,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.size(56.dp),
                    ) { Icon(if (playPause.showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (playPause.showPlay) "Play" else "Pause") }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "${Fmt.duration(progress.currentPositionMs)} / ${Fmt.duration(progress.durationMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private class PdfDocument(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount

    suspend fun aspect(index: Int): Float = lock.withLock {
        withContext(Dispatchers.IO) { renderer.openPage(index).use { it.width.toFloat() / it.height.toFloat() } }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

@Composable
private fun PdfViewer(url: String, name: String) {
    val context = LocalContext.current
    val container = LocalContainer.current
    var attempt by remember { mutableStateOf(0) }
    val document by produceState<Result<PdfDocument>?>(null, url, attempt) {
        value = runCatching { PdfDocument(fetchToCache(container.httpClient, context, url, name)) }
    }
    DisposableEffect(document) { onDispose { document?.getOrNull()?.close() } }

    when (val doc = document) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
        else -> doc.fold(
            onSuccess = { pdf ->
                val list = rememberLazyListState()
                val current by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 1000.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(pdf.pageCount) { index -> PdfPage(pdf, index) }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
                    ) {
                        Text("$current / ${pdf.pageCount}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            },
            onFailure = { FileCard(url, name, error = "This PDF couldn't be displayed.", onRetry = { attempt++ }) },
        )
    }
}

@Composable
private fun PdfPage(pdf: PdfDocument, index: Int) {
    var widthPx by remember { mutableStateOf(0) }
    val aspect by produceState(0.707f, pdf, index) { value = runCatching { pdf.aspect(index) }.getOrDefault(0.707f) }
    val bitmap by produceState<Bitmap?>(null, pdf, index, widthPx) {
        if (widthPx > 0) value = runCatching { pdf.render(index, widthPx.coerceAtMost(2400)) }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .onSizeChanged { widthPx = it.width },
    ) {
        bitmap?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

@Composable
private fun FileCard(url: String, name: String, error: String? = null, onRetry: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeBadge(Icons.Rounded.InsertDriveFile, polygon = MaterialShapes.Cookie7Sided, size = 104.dp)
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    error ?: (name.substringAfterLast('.', "").uppercase().ifBlank { "File" } + " file"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onRetry != null) FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Retry") }
                    FilledTonalButton(onClick = { context.download(url, name); messenger.show("Downloading $name") }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download")
                    }
                    Button(
                        onClick = {
                            opening = true
                            scope.launch {
                                runCatching {
                                    val file = fetchToCache(container.httpClient, context, url, name)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(Intent.createChooser(intent, "Open with"))
                                }.onFailure { messenger.error(it) }
                                opening = false
                            }
                        },
                        enabled = !opening,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(if (opening) "Opening…" else "Open with…") }
                }
            }
        }
    }
}
