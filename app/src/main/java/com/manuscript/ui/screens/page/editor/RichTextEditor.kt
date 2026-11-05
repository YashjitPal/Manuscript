package com.manuscript.ui.screens.page.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.Hue
import com.manuscript.core.model.RichText
import com.manuscript.core.util.CodeHighlighter
import com.manuscript.ui.components.RichColors
import com.manuscript.ui.components.richColors
import com.manuscript.ui.components.toSpanStyle
import com.manuscript.ui.theme.LocalNotionPalette
import com.manuscript.ui.theme.NotionPalette
import kotlinx.coroutines.launch

enum class MenuKind { SLASH, MENTION }

@Stable
interface EditorCallbacks {
    fun onSplit(right: List<RichText>)
    fun onBackspaceAtStart()
    fun onShortcut(type: BlockType)
    fun onFocusPrevious()
    fun onFocusNext()
    fun onIndent()
    fun onOutdent()
    fun onMenu(kind: MenuKind?, start: Int, query: String)
    fun onChanged()
    fun onDone()
    fun onLinkRequest()
    fun onFocusLost()
}

private class EditorFlags {
    var split: Int? = null
    var backspace = false
}

@Composable
fun codeStyles(): Map<CodeHighlighter.Kind, SpanStyle> {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalNotionPalette.current
    return remember(scheme, palette) {
        mapOf(
            CodeHighlighter.Kind.KEYWORD to SpanStyle(color = scheme.primary, fontWeight = FontWeight.SemiBold),
            CodeHighlighter.Kind.STRING to SpanStyle(color = palette.accent(Hue.GREEN)),
            CodeHighlighter.Kind.COMMENT to SpanStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.75f), fontStyle = FontStyle.Italic),
            CodeHighlighter.Kind.NUMBER to SpanStyle(color = palette.accent(Hue.ORANGE)),
            CodeHighlighter.Kind.TYPE to SpanStyle(color = scheme.tertiary),
            CodeHighlighter.Kind.FUNCTION to SpanStyle(color = palette.accent(Hue.BLUE)),
            CodeHighlighter.Kind.ANNOTATION to SpanStyle(color = palette.accent(Hue.PURPLE)),
            CodeHighlighter.Kind.CONSTANT to SpanStyle(color = palette.accent(Hue.PINK)),
            CodeHighlighter.Kind.TAG to SpanStyle(color = palette.accent(Hue.RED)),
        )
    }
}

fun Attrs.editorStyle(palette: NotionPalette, colors: RichColors): SpanStyle {
    val base = annotations.toSpanStyle(palette, colors)
    return when {
        mention != null -> base.merge(SpanStyle(color = colors.link, fontWeight = FontWeight.SemiBold))
        equation != null -> base.merge(SpanStyle(fontStyle = FontStyle.Italic, color = colors.mention))
        link != null -> base.merge(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))
        else -> base
    }
}

@Composable
fun RichTextEditor(
    state: RichEditState,
    style: TextStyle,
    callbacks: EditorCallbacks,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    code: Boolean = false,
    language: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    color: Color = Color.Unspecified,
) {
    val palette = LocalNotionPalette.current
    val colors = richColors()
    val codeColors = codeStyles()
    val scope = rememberCoroutineScope()
    val flags = remember(state) { EditorFlags() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color

    val output = remember(state, palette, colors, code, language, codeColors) {
        OutputTransformation {
            if (code) {
                val text = asCharSequence().toString()
                CodeHighlighter.highlight(text.drop(1), language).forEach { token ->
                    val start = (token.start + 1).coerceAtMost(length)
                    val end = (token.end + 1).coerceAtMost(length)
                    if (end > start) codeColors[token.kind]?.let { addStyle(it, start, end) }
                }
            } else {
                var pos = 1
                state.runs.forEach { run ->
                    val end = (pos + run.length).coerceAtMost(length)
                    if (end > pos) {
                        val s = run.attrs.editorStyle(palette, colors)
                        if (s != SpanStyle()) addStyle(s, pos, end)
                    }
                    pos += run.length
                }
            }
        }
    }

    val input = remember(state, code) {
        InputTransformation {
            val chars = asCharSequence()
            if (chars.isEmpty() || chars[0] != RichEditState.SENTINEL) {
                revertAllChanges()
                flags.backspace = true
                scope.launch {
                    if (flags.backspace) {
                        flags.backspace = false
                        callbacks.onBackspaceAtStart()
                    }
                }
                return@InputTransformation
            }
            if (!code && !state.softBreak) {
                for (i in 0 until changes.changeCount) {
                    val range = changes.getRange(i)
                    val inserted = asCharSequence().subSequence(range.min, range.max)
                    val nl = inserted.indexOf('\n')
                    if (nl >= 0) {
                        val at = range.min + nl
                        replace(at, at + 1, "")
                        flags.split = at - 1
                        break
                    }
                }
            }
            state.softBreak = false
            if (selection.min == 0) selection = TextRange(maxOf(1, selection.start), maxOf(1, selection.end))
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.textState.text.toString() to state.textState.selection }.collect {
            state.sync()
            flags.split?.let { at ->
                flags.split = null
                val right = state.splitAt(at.coerceIn(0, state.content.length))
                callbacks.onSplit(right)
                return@collect
            }
            if (!code) {
                state.pendingShortcut()?.let { (prefix, type) ->
                    state.deleteRange(0, prefix.length)
                    callbacks.onShortcut(type)
                    return@collect
                }
                detectMenu(state, callbacks)
            }
            callbacks.onChanged()
        }
    }

    BasicTextField(
        state = state.textState,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (!it.isFocused) callbacks.onFocusLost() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val primary = event.isCtrlPressed || event.isMetaPressed
                val sel = state.selection
                when {
                    event.key == Key.Enter && event.isShiftPressed -> {
                        state.softBreak = true
                        false
                    }
                    event.key == Key.Enter && !code && !primary -> {
                        flags.split = null
                        val right = state.splitAt(sel.start)
                        callbacks.onSplit(right)
                        true
                    }
                    event.key == Key.Enter && code && primary -> {
                        callbacks.onDone(); true
                    }
                    event.key == Key.Backspace && sel.collapsed && sel.start == 0 -> {
                        callbacks.onBackspaceAtStart(); true
                    }
                    event.key == Key.Tab && !code -> {
                        if (event.isShiftPressed) callbacks.onOutdent() else callbacks.onIndent(); true
                    }
                    event.key == Key.DirectionUp && !event.isShiftPressed -> {
                        val l = layout
                        val offset = state.textState.selection.start.coerceIn(0, state.textState.text.length)
                        if (l != null && l.getLineForOffset(offset) == 0) {
                            callbacks.onFocusPrevious(); true
                        } else false
                    }
                    event.key == Key.DirectionDown && !event.isShiftPressed -> {
                        val l = layout
                        val offset = state.textState.selection.start.coerceIn(0, state.textState.text.length)
                        if (l != null && l.getLineForOffset(offset) == l.lineCount - 1) {
                            callbacks.onFocusNext(); true
                        } else false
                    }
                    event.key == Key.Escape -> {
                        callbacks.onDone(); true
                    }
                    primary && !code && event.key == Key.B -> { state.toggle(Mark.BOLD); callbacks.onChanged(); true }
                    primary && !code && event.key == Key.I -> { state.toggle(Mark.ITALIC); callbacks.onChanged(); true }
                    primary && !code && event.key == Key.U -> { state.toggle(Mark.UNDERLINE); callbacks.onChanged(); true }
                    primary && !code && event.key == Key.E -> { state.toggle(Mark.CODE); callbacks.onChanged(); true }
                    primary && !code && event.isShiftPressed && event.key == Key.X -> { state.toggle(Mark.STRIKE); callbacks.onChanged(); true }
                    primary && !code && event.key == Key.K -> { callbacks.onLinkRequest(); true }
                    else -> false
                }
            },
        inputTransformation = input,
        outputTransformation = output,
        textStyle = style.copy(color = textColor),
        keyboardOptions = KeyboardOptions(
            capitalization = if (code) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !code,
            imeAction = if (code || !state.multiline) ImeAction.Done else ImeAction.None,
        ),
        onKeyboardAction = KeyboardActionHandler {
            if (code || !state.multiline) {
                callbacks.onDone()
            } else {
                flags.split = null
                val right = state.splitAt(state.selection.start)
                callbacks.onSplit(right)
            }
        },
        lineLimits = if (state.multiline) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
        onTextLayout = { getResult -> layout = getResult() },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorator = TextFieldDecorator { inner ->
            Box {
                if (placeholder != null && state.content.isEmpty()) {
                    Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                }
                inner()
            }
        },
    )
}

private fun detectMenu(state: RichEditState, callbacks: EditorCallbacks) {
    val text = state.content
    val sel = state.selection
    if (!sel.collapsed) {
        callbacks.onMenu(null, 0, "")
        return
    }
    val cursor = sel.start.coerceAtMost(text.length)
    var i = cursor - 1
    while (i >= 0 && !text[i].isWhitespace() && text[i] != '/' && text[i] != '@' && cursor - i <= 30) i--
    if (i >= 0 && (text[i] == '/' || text[i] == '@') && (i == 0 || text[i - 1].isWhitespace())) {
        val attrs = state.attrsAt(i + 1)
        if (attrs.atomic) {
            callbacks.onMenu(null, 0, "")
            return
        }
        val kind = if (text[i] == '/') MenuKind.SLASH else MenuKind.MENTION
        callbacks.onMenu(kind, i, text.substring(i + 1, cursor))
    } else callbacks.onMenu(null, 0, "")
}
