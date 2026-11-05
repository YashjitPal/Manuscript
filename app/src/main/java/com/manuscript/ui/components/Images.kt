package com.manuscript.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NFile
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NodeKind
import com.manuscript.ui.theme.LocalNotionPalette
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Notion-hosted file URLs are signed and expire hourly; strip the signature for a stable cache key. */
fun stableImageKey(url: String): String {
    val lower = url.lowercase()
    val signed = lower.contains("amazonaws.com") || lower.contains("notion-static.com") || lower.contains("file.notion.so") ||
        lower.contains("notionusercontent.com") || lower.contains("x-amz-")
    return if (signed) url.substringBefore('?') else url
}

private val amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** True once a signed S3 link has expired, so fetching it would only return 403. */
fun isExpiredSignedUrl(url: String, now: Instant = Instant.now()): Boolean {
    if (!url.contains("X-Amz-Date=", ignoreCase = true)) return false
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val names = uri.queryParameterNames
    val dateParam = names.firstOrNull { it.equals("X-Amz-Date", ignoreCase = true) } ?: return false
    val expiresParam = names.firstOrNull { it.equals("X-Amz-Expires", ignoreCase = true) } ?: return false
    val signedAt = runCatching { Instant.from(amzDate.parse(uri.getQueryParameter(dateParam))) }.getOrNull() ?: return false
    val lifetime = uri.getQueryParameter(expiresParam)?.toLongOrNull() ?: return false
    return now.isAfter(signedAt.plusSeconds(lifetime - 30))
}

/** Unsplash serves originals (often 5000px+) unless asked for a size; Notion covers rarely need more than 1600px. */
fun optimizedImageUrl(url: String): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
    if (uri.host != "images.unsplash.com" || uri.getQueryParameter("w") != null) return url
    return uri.buildUpon().appendQueryParameter("w", "1600").appendQueryParameter("fit", "max").build().toString()
}

@Composable
fun rememberImageRequest(url: String, crossfade: Boolean = true): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(url) { imageRequest(context, url, crossfade) }
}

private fun imageRequest(context: PlatformContext, url: String, crossfade: Boolean): ImageRequest {
    val key = stableImageKey(url)
    return ImageRequest.Builder(context)
        .data(optimizedImageUrl(url))
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .crossfade(crossfade)
        .apply { if (isExpiredSignedUrl(url)) networkCachePolicy(CachePolicy.DISABLED) }
        .build()
}

@Composable
fun NotionImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val loaded = state is AsyncImagePainter.State.Success
    AsyncImage(
        model = rememberImageRequest(url),
        contentDescription = contentDescription,
        modifier = modifier
            .background(if (loaded) Color.Transparent else placeholderColor)
            .then(if (state is AsyncImagePainter.State.Loading) Modifier.shimmer() else Modifier),
        contentScale = contentScale,
        alignment = alignment,
        onState = { state = it },
    )
}

@Composable
fun NotionIcon(
    icon: NIcon?,
    size: Dp,
    modifier: Modifier = Modifier,
    kind: NodeKind = NodeKind.PAGE,
    fallback: ImageVector? = null,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showFallback: Boolean = true,
) {
    val density = LocalDensity.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (icon) {
            is NIcon.Emoji -> Text(
                text = icon.emoji,
                style = TextStyle(fontSize = with(density) { (size * 0.82f).toSp() }, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            is NIcon.External -> AsyncImage(rememberImageRequest(icon.url), null, Modifier.size(size).clip(RoundedCornerShape(size * 0.18f)), contentScale = ContentScale.Fit)
            is NIcon.Hosted -> AsyncImage(rememberImageRequest(icon.url), null, Modifier.size(size).clip(RoundedCornerShape(size * 0.18f)), contentScale = ContentScale.Fit)
            is NIcon.CustomEmoji -> icon.url?.let { AsyncImage(rememberImageRequest(it), icon.name, Modifier.size(size * 0.9f), contentScale = ContentScale.Fit) }
            is NIcon.Native -> AsyncImage(rememberImageRequest(icon.url, crossfade = false), icon.name, Modifier.size(size * 0.92f), contentScale = ContentScale.Fit)
            is NIcon.Upload -> Unit
            null -> if (showFallback) Icon(
                imageVector = fallback ?: when (kind) {
                    NodeKind.PAGE -> Icons.AutoMirrored.Rounded.Article
                    NodeKind.DATABASE, NodeKind.DATA_SOURCE -> Icons.Rounded.TableChart
                },
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.size(size * 0.86f),
            )
        }
    }
}

@Composable
fun PageCover(cover: NFile, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    NotionImage(cover.url, modifier, contentScale = ContentScale.Crop, alignment = alignment)
}

private val avatarHues = listOf(Hue.BLUE, Hue.PURPLE, Hue.PINK, Hue.ORANGE, Hue.GREEN, Hue.RED, Hue.YELLOW, Hue.BROWN)

@Composable
fun UserAvatar(
    user: NUser?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    ring: Color? = null,
) {
    val palette = LocalNotionPalette.current
    val hue = avatarHues[((user?.id?.hashCode() ?: 0) and Int.MAX_VALUE) % avatarHues.size]
    val ringModifier = if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier
    Box(
        modifier
            .size(size)
            .then(ringModifier)
            .clip(shape)
            .background(palette.option(hue)),
        contentAlignment = Alignment.Center,
    ) {
        val url = user?.avatarUrl?.takeIf { it.isNotBlank() }
        if (url != null) {
            NotionImage(url, Modifier.size(size), contentDescription = user.displayName, placeholderColor = palette.option(hue))
        } else {
            val density = LocalDensity.current
            Text(
                text = user?.initials ?: "?",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = with(density) { (size * 0.38f).toSp() },
                    fontWeight = FontWeight.Bold,
                ),
                color = palette.accent(hue),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AvatarStack(users: List<NUser>, size: Dp, modifier: Modifier = Modifier, max: Int = 4) {
    val ring = MaterialTheme.colorScheme.surface
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        users.take(max).forEachIndexed { index, user ->
            UserAvatar(user, size, Modifier.offset(x = -(size * 0.3f) * index), ring = ring)
        }
        if (users.size > max) {
            Box(
                Modifier
                    .offset(x = -(size * 0.3f) * max)
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("+${users.size - max}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
fun TintedIcon(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
