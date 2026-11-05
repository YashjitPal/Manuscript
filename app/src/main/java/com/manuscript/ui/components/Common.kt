package com.manuscript.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.manuscript.core.model.Hue
import com.manuscript.core.model.SelectOption
import com.manuscript.core.net.isOffline
import com.manuscript.core.net.userMessage
import com.manuscript.ui.theme.LocalNotionPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    return drawWithContent {
        drawContent()
        val x = size.width * progress
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - size.width * 0.5f, 0f),
                end = Offset(x + size.width * 0.5f, size.height),
            ),
        )
    }
}

@Composable
fun SkeletonLine(width: Float, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .shimmer(),
    )
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, lines: Int = 4) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(lines) { i -> SkeletonLine(if (i == lines - 1) 0.55f else 0.92f - (i % 3) * 0.08f) }
    }
}

/** An indeterminate expressive loading indicator, centered in the available space. */
@Composable
fun LoadingPane(modifier: Modifier = Modifier, contained: Boolean = true) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (contained) ContainedLoadingIndicator(Modifier.size(64.dp)) else LoadingIndicator(Modifier.size(56.dp))
    }
}

/** A Material shape (cookie, clover, …) filled with a color and an icon in the middle. */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 96.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    rotate: Boolean = false,
) {
    val rotation = if (rotate) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin").value
    } else 0f
    val shape = polygon.toShape()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(container),
        )
        Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeBadge(icon, polygon = polygon, size = 112.dp, rotate = true)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun ErrorState(error: Throwable, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = if (error.isOffline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
        title = if (error.isOffline) "You're offline" else "Something went wrong",
        message = error.userMessage(),
        polygon = if (error.isOffline) MaterialShapes.Clover4Leaf else MaterialShapes.SoftBurst,
        modifier = modifier,
        action = onRetry?.let { retry -> { FilledTonalButton(onClick = retry, shapes = ButtonDefaults.shapes()) { Text("Try again") } } },
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun OptionChip(
    option: SelectOption,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val palette = LocalNotionPalette.current
    Surface(
        color = palette.option(option.color),
        contentColor = palette.optionContent,
        shape = RoundedCornerShape(if (small) 6.dp else 8.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = if (small) 6.dp else 9.dp, vertical = if (small) 1.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(4.dp))
            }
            Text(
                option.name.ifBlank { "Untitled" },
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusDot(hue: Hue, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val palette = LocalNotionPalette.current
    Box(modifier.size(size).clip(RoundedCornerShape(50)).background(palette.accent(hue)))
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
                shapes = ButtonDefaults.shapes(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
    )
}

/** App-wide snackbar access so any screen can report results and errors. */
@Stable
class Messenger(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, action: String? = null, long: Boolean = false, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = action,
                withDismissAction = action == null && long,
                duration = if (long || action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun error(t: Throwable) = show(t.userMessage(), long = true)
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("No Messenger provided") }

/** A Shape that morphs between two Material polygons. */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        path.translate(Offset(size.width / 2, size.height / 2) - path.getBounds().center)
        if (rotation != 0f) {
            val rotate = Matrix()
            rotate.translate(size.width / 2, size.height / 2)
            rotate.rotateZ(rotation)
            rotate.translate(-size.width / 2, -size.height / 2)
            path.transform(rotate)
        }
        return Outline.Generic(path)
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)
