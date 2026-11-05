package com.manuscript.ui.screens.page.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.NBlock
import com.manuscript.core.model.NColor
import com.manuscript.core.model.NIcon
import com.manuscript.core.model.NodeKind
import com.manuscript.core.model.plainText
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.components.RichTextView
import com.manuscript.ui.screens.page.FlatRow
import com.manuscript.ui.screens.page.PageViewModel
import com.manuscript.ui.screens.page.editor.EditorCallbacks
import com.manuscript.ui.screens.page.editor.RichTextEditor
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import com.manuscript.ui.theme.LocalReading
import com.manuscript.ui.theme.ReadingStyles

/** Everything a block renderer needs: the page state holder plus navigation callbacks. */
@Stable
class BlockScope(
    val vm: PageViewModel,
    val editable: Boolean,
    val wide: Boolean,
    val callbacksFor: (String) -> EditorCallbacks,
    val onOpenPage: (String) -> Unit,
    val onOpenDatabase: (String) -> Unit,
    val onOpenMedia: (url: String, kind: String, name: String?) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onBlockMenu: (key: String) -> Unit,
    val onScrollTo: (key: String) -> Unit,
    val onComment: (blockId: String) -> Unit,
    val pageIcon: (String) -> NIcon?,
    val pageTitle: (String) -> String?,
)

fun indentFor(depth: Int): Dp = (depth * 26).dp

fun ReadingStyles.forType(type: BlockType): TextStyle = when (type) {
    BlockType.HEADING_1 -> h1
    BlockType.HEADING_2 -> h2
    BlockType.HEADING_3 -> h3
    BlockType.HEADING_4 -> h4
    BlockType.QUOTE -> quote
    BlockType.CODE -> code
    else -> body
}

private fun topSpace(type: BlockType): Dp = when (type) {
    BlockType.HEADING_1 -> 26.dp
    BlockType.HEADING_2 -> 20.dp
    BlockType.HEADING_3 -> 14.dp
    BlockType.HEADING_4 -> 10.dp
    else -> 2.dp
}

/** Renders one flattened row of the page. */
@Composable
fun BlockRow(row: FlatRow, scope: BlockScope, editingKey: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = indentFor(row.depth), top = topSpace(row.block.type), bottom = 2.dp),
    ) {
        BlockView(row.key, row.block, scope, editingKey, number = row.number, depth = row.depth)
    }
}

/** Renders a list of blocks inline (inside callouts, columns, tabs and quotes). */
@Composable
fun BlockList(blocks: List<NBlock>, scope: BlockScope, editingKey: String?, depth: Int = 0) {
    val state by scope.vm.state.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        var number = 0
        blocks.forEach { block ->
            number = if (block.type == BlockType.NUMBERED) number + 1 else 0
            val start = if (block.type == BlockType.NUMBERED && number == 1) (block.listStart ?: 1) else null
            if (start != null) number = start
            val key = scope.vm.keyFor(block.id)
            Box(Modifier.fillMaxWidth().padding(top = topSpace(block.type), bottom = 2.dp)) {
                BlockView(key, block, scope, editingKey, number, depth)
            }
            val nested = block.type.isListItem || block.type == BlockType.PARAGRAPH || block.type == BlockType.SYNCED_BLOCK
            val open = block.type != BlockType.TOGGLE || key in state.openToggles
            if (nested && open && block.children.isNotEmpty()) {
                Box(Modifier.padding(start = 26.dp)) { BlockList(block.children, scope, editingKey, depth + 1) }
            }
        }
    }
}

@Composable
fun BlockView(key: String, block: NBlock, scope: BlockScope, editingKey: String?, number: Int, depth: Int) {
    val reading = LocalReading.current
    val palette = LocalNotionPalette.current
    val background = palette.backgroundColor(block.color)
    val bgModifier = if (background != Color.Unspecified) Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier
    when (block.type) {
        BlockType.PARAGRAPH, BlockType.HEADING_1, BlockType.HEADING_2, BlockType.HEADING_3, BlockType.HEADING_4 -> {
            if (block.type.isHeading && block.toggleable) {
                ToggleRow(key, block, scope, editingKey, bgModifier)
            } else {
                TextBlock(key, block, scope, editingKey, reading.forType(block.type), bgModifier, placeholder = placeholderFor(block.type))
            }
        }
        BlockType.BULLETED -> ListItemRow(marker = { Bullet(depth, reading.body) }, modifier = bgModifier) {
            TextBlock(key, block, scope, editingKey, reading.body, placeholder = "List")
        }
        BlockType.NUMBERED -> ListItemRow(marker = { NumberMarker(number, depth, block.listFormat, reading.body) }, modifier = bgModifier) {
            TextBlock(key, block, scope, editingKey, reading.body, placeholder = "List")
        }
        BlockType.TO_DO -> ListItemRow(
            marker = {
                TodoCheckbox(block.checked, enabled = scope.editable) { scope.vm.setChecked(key, it) }
            },
            modifier = bgModifier,
        ) {
            TextBlock(
                key, block, scope, editingKey, reading.body,
                color = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                strike = block.checked,
                placeholder = "To-do",
            )
        }
        BlockType.TOGGLE -> ToggleRow(key, block, scope, editingKey, bgModifier)
        BlockType.QUOTE -> QuoteBlock(key, block, scope, editingKey, bgModifier)
        BlockType.CALLOUT -> CalloutBlock(key, block, scope, editingKey)
        BlockType.CODE -> CodeBlock(key, block, scope, editingKey)
        BlockType.EQUATION -> EquationBlock(key, block, scope)
        BlockType.DIVIDER -> Box(Modifier.fillMaxWidth().blockGestures(key, scope).padding(vertical = 10.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        BlockType.IMAGE -> ImageBlock(key, block, scope)
        BlockType.VIDEO, BlockType.AUDIO, BlockType.PDF, BlockType.FILE -> MediaBlock(key, block, scope)
        BlockType.BOOKMARK, BlockType.EMBED, BlockType.LINK_PREVIEW -> LinkBlock(key, block, scope)
        BlockType.CHILD_PAGE -> PageLinkRow(key, block.id, block.title ?: scope.pageTitle(block.id) ?: "Untitled", scope.pageIcon(block.id), NodeKind.PAGE, scope) {
            scope.onOpenPage(block.id)
        }
        BlockType.CHILD_DATABASE -> InlineDatabase(key, block, scope)
        BlockType.LINK_TO_PAGE -> {
            val target = block.linkPageId ?: block.linkDatabaseId ?: ""
            val isDb = block.linkPageId == null
            PageLinkRow(key, target, scope.pageTitle(target) ?: if (isDb) "Linked database" else "Linked page", scope.pageIcon(target),
                if (isDb) NodeKind.DATABASE else NodeKind.PAGE, scope, linked = true) {
                if (isDb) scope.onOpenDatabase(target) else scope.onOpenPage(target)
            }
        }
        BlockType.TABLE -> TableBlock(key, block, scope)
        BlockType.TABLE_ROW -> Unit
        BlockType.COLUMN_LIST -> ColumnsBlock(block, scope, editingKey)
        BlockType.COLUMN -> BlockList(block.children, scope, editingKey)
        BlockType.SYNCED_BLOCK -> SyncedHeader(key, block, scope)
        BlockType.TEMPLATE -> TemplateButton(block, reading.body)
        BlockType.TABLE_OF_CONTENTS -> TableOfContents(scope, block.color)
        BlockType.BREADCRUMB -> Breadcrumb(scope)
        BlockType.TAB -> TabsBlock(key, block, scope, editingKey)
        BlockType.MEETING_NOTES -> MeetingNotesBlock(key, block, scope, editingKey)
        BlockType.UNSUPPORTED, BlockType.UNKNOWN -> UnsupportedBlock(key, block, scope)
    }
}

private fun placeholderFor(type: BlockType) = when (type) {
    BlockType.HEADING_1 -> "Heading 1"
    BlockType.HEADING_2 -> "Heading 2"
    BlockType.HEADING_3 -> "Heading 3"
    BlockType.HEADING_4 -> "Heading 4"
    else -> "Type ‘/’ for commands"
}

/** Long-press opens the block menu; used by non-text blocks. */
@Composable
fun Modifier.blockGestures(key: String, scope: BlockScope, onTap: (() -> Unit)? = null): Modifier {
    val haptics = LocalHaptics.current
    return pointerInput(key, onTap) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onLongPress = {
                if (scope.editable) {
                    haptics.longPress()
                    scope.onBlockMenu(key)
                }
            },
        )
    }
}

@Composable
fun TextBlock(
    key: String,
    block: NBlock,
    scope: BlockScope,
    editingKey: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strike: Boolean = false,
    placeholder: String? = null,
) {
    val palette = LocalNotionPalette.current
    val textColor = palette.textColor(block.color).takeIf { it != Color.Unspecified } ?: color
    val effective = if (strike) style.copy(textDecoration = TextDecoration.LineThrough) else style
    val editState = scope.vm.editState
    if (editingKey == key && editState != null) {
        val focus = remember { FocusRequester() }
        val target by scope.vm.focus.collectAsState()
        LaunchedEffect(target) {
            val t = target
            if (t != null && t.key == key) {
                runCatching { focus.requestFocus() }
                editState.placeCursor(t.cursor)
                scope.vm.consumeFocus()
            }
        }
        RichTextEditor(
            state = editState,
            style = effective,
            callbacks = scope.callbacksFor(key),
            modifier = modifier.fillMaxWidth(),
            placeholder = placeholder,
            focusRequester = focus,
            color = textColor,
        )
    } else {
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val haptics = LocalHaptics.current
        val isEmpty = block.rich.isEmpty()
        Box(
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = with(androidx.compose.ui.platform.LocalDensity.current) { effective.lineHeight.toDp() })
                .pointerInput(key, scope.editable) {
                    detectTapGestures(
                        onTap = { pos ->
                            if (scope.editable) {
                                val offset = layout?.getOffsetForPosition(pos) ?: block.plainText.length
                                scope.vm.startEditing(key, offset)
                            }
                        },
                        onLongPress = {
                            if (scope.editable) {
                                haptics.longPress()
                                scope.onBlockMenu(key)
                            }
                        },
                    )
                },
        ) {
            if (!isEmpty) {
                RichTextView(block.rich, effective, color = textColor, onTextLayout = { layout = it })
            } else if (scope.editable && block.type != BlockType.PARAGRAPH && placeholder != null) {
                Text(placeholder, style = effective, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun ListItemRow(marker: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reading = LocalReading.current
    val lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { reading.body.lineHeight.toDp() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(26.dp).height(lineHeight), contentAlignment = Alignment.Center) { marker() }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Bullet(depth: Int, style: TextStyle) {
    val glyph = when (depth % 3) { 0 -> "•"; 1 -> "◦"; else -> "▪" }
    Text(glyph, style = style.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
}

private fun toLetters(n: Int): String {
    var x = n.coerceAtLeast(1)
    val sb = StringBuilder()
    while (x > 0) {
        x--
        sb.insert(0, ('a' + x % 26))
        x /= 26
    }
    return sb.toString()
}

private fun toRoman(n: Int): String {
    val values = listOf(1000 to "m", 900 to "cm", 500 to "d", 400 to "cd", 100 to "c", 90 to "xc", 50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i")
    var x = n.coerceAtLeast(1)
    val sb = StringBuilder()
    values.forEach { (v, s) -> while (x >= v) { sb.append(s); x -= v } }
    return sb.toString()
}

@Composable
private fun NumberMarker(number: Int, depth: Int, format: String?, style: TextStyle) {
    val label = when (format ?: listOf("numbers", "letters", "roman")[depth % 3]) {
        "letters" -> toLetters(number)
        "roman" -> toRoman(number)
        else -> number.toString()
    }
    Text("$label.", style = style.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
}

@Composable
fun TodoCheckbox(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (checked) scheme.primary else Color.Transparent, label = "todo")
    val scale by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "check")
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(if (checked) 10.dp else 6.dp))
            .background(container)
            .border(2.dp, if (checked) scheme.primary else scheme.onSurfaceVariant, RoundedCornerShape(if (checked) 10.dp else 6.dp))
            .clickable(enabled = enabled, role = Role.Checkbox) { haptics.toggle(!checked); onChange(!checked) }
            .semantics { stateDescription = if (checked) "Checked" else "Unchecked" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, null, Modifier.size(15.dp).scale(scale), tint = scheme.onPrimary)
    }
}

@Composable
private fun ToggleRow(key: String, block: NBlock, scope: BlockScope, editingKey: String?, modifier: Modifier) {
    val reading = LocalReading.current
    val state by scope.vm.state.collectAsState()
    val open = key in state.openToggles
    val rotation by animateFloatAsState(if (open) 90f else 0f, spring(stiffness = 600f), label = "toggle")
    val haptics = LocalHaptics.current
    val style = reading.forType(block.type)
    val lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { style.lineHeight.toDp() }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(26.dp, lineHeight)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { haptics.tick(); scope.vm.toggleOpen(key) }
                    .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ChevronRight, if (open) "Collapse" else "Expand", Modifier.size(22.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurface)
            }
            Box(Modifier.weight(1f)) {
                TextBlock(key, block, scope, editingKey, style, placeholder = "Toggle")
            }
        }
        if (open && block.children.isEmpty() && !block.hasChildren && scope.editable) {
            Text(
                "Empty toggle. Tap to add a block inside.",
                style = reading.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 26.dp, top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { scope.vm.insertBlock(NBlock("", BlockType.PARAGRAPH), afterKey = null, parentKey = key) }
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun QuoteBlock(key: String, block: NBlock, scope: BlockScope, editingKey: String?, modifier: Modifier) {
    val reading = LocalReading.current
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            TextBlock(key, block, scope, editingKey, reading.quote, placeholder = "Quote")
            if (block.children.isNotEmpty()) BlockList(block.children, scope, editingKey)
        }
    }
}

@Composable
private fun CalloutBlock(key: String, block: NBlock, scope: BlockScope, editingKey: String?) {
    val reading = LocalReading.current
    val palette = LocalNotionPalette.current
    val bg = palette.backgroundColor(block.color).takeIf { it != Color.Unspecified }
        ?: if (block.color.isDefault) MaterialTheme.colorScheme.surfaceContainerHigh else palette.background(block.color.hue)
    val lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { reading.body.lineHeight.toDp() }
    Surface(color = bg, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Box(Modifier.height(lineHeight).blockGestures(key, scope) { if (scope.editable) scope.onBlockMenu(key) }, contentAlignment = Alignment.Center) {
                NotionIcon(block.icon ?: NIcon.Emoji("💡"), 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TextBlock(key, block, scope, editingKey, reading.body, color = palette.textColor(NColor(block.color.hue)).takeIf { block.color.hue != com.manuscript.core.model.Hue.DEFAULT && !block.color.background } ?: Color.Unspecified, placeholder = "Callout")
                if (block.children.isNotEmpty()) BlockList(block.children, scope, editingKey)
            }
        }
    }
}

@Composable
fun PageLinkRow(
    key: String,
    id: String,
    title: String,
    icon: NIcon?,
    kind: NodeKind,
    scope: BlockScope,
    linked: Boolean = false,
    onOpen: () -> Unit,
) {
    val reading = LocalReading.current
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(id) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { if (scope.editable) { haptics.longPress(); scope.onBlockMenu(key) } },
                )
            }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            NotionIcon(icon, 24.dp, kind = kind)
            if (linked) Icon(Icons.AutoMirrored.Rounded.CallMade, null, Modifier.size(12.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title.ifBlank { "Untitled" },
            style = reading.body.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncedHeader(key: String, block: NBlock, scope: BlockScope) {
    if (!scope.editable) return
    Row(
        Modifier.blockGestures(key, scope).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Sync, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(6.dp))
        Text(
            if (block.syncedFrom == null) "Synced block" else "Synced from another page",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TemplateButton(block: NBlock, style: TextStyle) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(block.plainText.ifBlank { "Template" }, style = style.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun TableOfContents(scope: BlockScope, color: NColor) {
    val state by scope.vm.state.collectAsState()
    val blocks = state.blocks
    val headings = remember(blocks) { blocks.flatMap { it.walk().toList() }.filter { it.type.isHeading } }
    val palette = LocalNotionPalette.current
    val tint = palette.textColor(color).takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.backgroundColor(color).takeIf { it != Color.Unspecified } ?: Color.Transparent)
            .padding(vertical = 4.dp),
    ) {
        if (headings.isEmpty()) {
            Text("Add headings to build a table of contents.", style = LocalReading.current.small, color = tint)
        }
        headings.forEach { h ->
            val level = when (h.type) { BlockType.HEADING_1 -> 0; BlockType.HEADING_2 -> 1; BlockType.HEADING_3 -> 2; else -> 3 }
            Text(
                h.plainText.ifBlank { "Untitled" },
                style = LocalReading.current.body.copy(textDecoration = TextDecoration.Underline),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { scope.onScrollTo(scope.vm.keyFor(h.id)) }
                    .padding(start = (level * 18).dp + 4.dp, top = 3.dp, bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun Breadcrumb(scope: BlockScope) {
    val state by scope.vm.state.collectAsState()
    val page = state.page
    val chain = remember(page) { scope.vm.parentChain() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        (chain + listOfNotNull(page?.let { com.manuscript.core.model.NodeRef(it.id, NodeKind.PAGE, it.title, it.icon) })).forEachIndexed { i, ref ->
            if (i > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { if (ref.kind == NodeKind.PAGE) scope.onOpenPage(ref.id) else scope.onOpenDatabase(ref.databaseId ?: ref.id) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotionIcon(ref.icon, 16.dp, kind = ref.kind)
                Spacer(Modifier.width(4.dp))
                Text(ref.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            }
        }
    }
}

@Composable
private fun UnsupportedBlock(key: String, block: NBlock, scope: BlockScope) {
    val state by scope.vm.state.collectAsState()
    val page = state.page
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().blockGestures(key, scope),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("This block isn't available through the Notion API", style = MaterialTheme.typography.titleSmall)
                Text(
                    (block.unsupportedType ?: block.rawType).replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            page?.url?.let { url -> TextButton(onClick = { scope.onOpenUrl(url) }) { Text("Open in Notion") } }
        }
    }
}