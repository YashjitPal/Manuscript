package com.manuscript.ui.screens.page.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.manuscript.core.model.Annotations
import com.manuscript.core.model.BlockType
import com.manuscript.core.model.Mention
import com.manuscript.core.model.NColor
import com.manuscript.core.model.RichText
import com.manuscript.core.model.plainText

/** Formatting attributes shared by a run of characters. */
@Immutable
data class Attrs(
    val annotations: Annotations = Annotations.None,
    val link: String? = null,
    val mention: Mention? = null,
    val equation: String? = null,
) {
    val atomic: Boolean get() = mention != null || equation != null
    fun plain(): Attrs = copy(mention = null, equation = null)
}

@Immutable
data class Run(val length: Int, val attrs: Attrs)

enum class Mark { BOLD, ITALIC, UNDERLINE, STRIKE, CODE }

private fun Annotations.has(mark: Mark) = when (mark) {
    Mark.BOLD -> bold
    Mark.ITALIC -> italic
    Mark.UNDERLINE -> underline
    Mark.STRIKE -> strikethrough
    Mark.CODE -> code
}

private fun Annotations.with(mark: Mark, on: Boolean) = when (mark) {
    Mark.BOLD -> copy(bold = on)
    Mark.ITALIC -> copy(italic = on)
    Mark.UNDERLINE -> copy(underline = on)
    Mark.STRIKE -> copy(strikethrough = on)
    Mark.CODE -> copy(code = on)
}

/** Markdown-style shortcuts typed at the start of a block. */
val MarkdownShortcuts: List<Pair<String, BlockType>> = listOf(
    "#### " to BlockType.HEADING_4,
    "### " to BlockType.HEADING_3,
    "## " to BlockType.HEADING_2,
    "# " to BlockType.HEADING_1,
    "[] " to BlockType.TO_DO,
    "[ ] " to BlockType.TO_DO,
    "- " to BlockType.BULLETED,
    "* " to BlockType.BULLETED,
    "+ " to BlockType.BULLETED,
    "1. " to BlockType.NUMBERED,
    "> " to BlockType.TOGGLE,
    "\" " to BlockType.QUOTE,
    "``` " to BlockType.CODE,
)

/**
 * Editable rich text: a [TextFieldState] holding the characters (prefixed with an invisible
 * sentinel so backspace at the start can be detected), plus runs of formatting attributes.
 */
@Stable
class RichEditState(initial: List<RichText>, cursor: Int = -1, val multiline: Boolean = true) {
    companion object {
        const val SENTINEL = '\u200B'

        fun runsFrom(rich: List<RichText>): List<Run> =
            rich.filter { it.text.isNotEmpty() }.map { Run(it.text.length, Attrs(it.annotations, it.link, it.mention, it.equation)) }
    }

    private val initialText = initial.plainText()
    val textState = TextFieldState(
        initialText = SENTINEL + initialText,
        initialSelection = TextRange(1 + (if (cursor < 0) initialText.length else cursor.coerceIn(0, initialText.length))),
    )

    var runs by mutableStateOf(runsFrom(initial))
        private set

    /** Attributes applied to the next typed characters when nothing is selected. */
    var typing by mutableStateOf<Attrs?>(null)

    /** True while Shift is held so a newline becomes a soft line break instead of a new block. */
    var softBreak = false

    private var lastContent = initialText
    private var lastSelection = textState.selection

    val content: String get() = textState.text.toString().let { if (it.startsWith(SENTINEL)) it.substring(1) else it }

    val selection: TextRange
        get() {
            val s = textState.selection
            return TextRange((s.start - 1).coerceAtLeast(0), (s.end - 1).coerceAtLeast(0))
        }

    /** Re-syncs formatting runs after the text changed (typing, IME, paste, undo). */
    fun sync() {
        val current = content
        val sel = textState.selection
        if (current == lastContent) {
            if (sel != lastSelection) {
                typing = null
                lastSelection = sel
            }
            return
        }
        val old = lastContent
        var p = 0
        val min = minOf(old.length, current.length)
        while (p < min && old[p] == current[p]) p++
        var s = 0
        while (s < min - p && old[old.length - 1 - s] == current[current.length - 1 - s]) s++
        val removedEnd = old.length - s
        val inserted = current.length - s - p
        runs = applyEdit(runs, p, removedEnd, inserted, insertionAttrs(p))
        if (runs.sumOf { it.length } != current.length) runs = listOf(Run(current.length, Attrs()))
        lastContent = current
        lastSelection = sel
    }

    private fun insertionAttrs(position: Int): Attrs {
        typing?.let { return it }
        if (position == 0) return runs.firstOrNull()?.attrs?.plain()?.copy(link = null) ?: Attrs()
        var pos = 0
        for (run in runs) {
            val end = pos + run.length
            if (position in (pos + 1)..end) {
                val inside = position < end
                return run.attrs.plain().copy(link = if (inside) run.attrs.link else null)
            }
            pos = end
        }
        return runs.lastOrNull()?.attrs?.plain()?.copy(link = null) ?: Attrs()
    }

    private fun applyEdit(source: List<Run>, start: Int, end: Int, insertLen: Int, insertAttrs: Attrs): List<Run> {
        val out = mutableListOf<Run>()
        var pos = 0
        var inserted = false
        fun insert() {
            if (!inserted) {
                if (insertLen > 0) out += Run(insertLen, insertAttrs)
                inserted = true
            }
        }
        for (run in source) {
            val rs = pos
            val re = pos + run.length
            when {
                re <= start -> out += run
                rs >= end -> {
                    insert()
                    out += run
                }
                else -> {
                    val before = (start - rs).coerceAtLeast(0)
                    val after = (re - end).coerceAtLeast(0)
                    val broken = run.attrs.atomic && ((end > start && (before > 0 || after > 0)) || (end == start && before > 0 && after > 0))
                    val keep = if (broken) run.attrs.plain() else run.attrs
                    if (before > 0) out += Run(before, keep)
                    insert()
                    if (after > 0) out += Run(after, keep)
                }
            }
            pos = re
        }
        insert()
        return merge(out)
    }

    private fun merge(list: List<Run>): List<Run> {
        val out = mutableListOf<Run>()
        for (run in list) {
            if (run.length <= 0) continue
            val last = out.lastOrNull()
            if (last != null && last.attrs == run.attrs && !run.attrs.atomic) out[out.lastIndex] = Run(last.length + run.length, last.attrs)
            else out += run
        }
        return out
    }

    private fun mapRange(start: Int, end: Int, transform: (Attrs) -> Attrs) {
        val out = mutableListOf<Run>()
        var pos = 0
        for (run in runs) {
            val rs = pos
            val re = pos + run.length
            if (re <= start || rs >= end) out += run
            else {
                val a = maxOf(start, rs)
                val b = minOf(end, re)
                if (a > rs) out += Run(a - rs, run.attrs)
                out += Run(b - a, transform(run.attrs))
                if (re > b) out += Run(re - b, run.attrs)
            }
            pos = re
        }
        runs = merge(out)
    }

    private fun runsIn(start: Int, end: Int): List<Run> {
        val out = mutableListOf<Run>()
        var pos = 0
        for (run in runs) {
            val re = pos + run.length
            if (re > start && pos < end) out += run
            pos = re
        }
        return out
    }

    fun attrsAt(position: Int): Attrs {
        var pos = 0
        for (run in runs) {
            val end = pos + run.length
            if (position in (pos + 1)..end) return run.attrs
            pos = end
        }
        return runs.firstOrNull()?.attrs ?: Attrs()
    }

    /** Attributes describing the current selection, for toolbar state. */
    fun currentAttrs(): Attrs {
        val sel = selection
        if (sel.collapsed) return typing ?: attrsAt(sel.start)
        val inRange = runsIn(sel.min, sel.max)
        if (inRange.isEmpty()) return Attrs()
        fun all(mark: Mark) = inRange.all { it.attrs.annotations.has(mark) }
        val firstColor = inRange.first().attrs.annotations.color
        return Attrs(
            annotations = Annotations(
                bold = all(Mark.BOLD), italic = all(Mark.ITALIC), underline = all(Mark.UNDERLINE),
                strikethrough = all(Mark.STRIKE), code = all(Mark.CODE),
                color = if (inRange.all { it.attrs.annotations.color == firstColor }) firstColor else NColor.Default,
            ),
            link = inRange.first().attrs.link.takeIf { link -> inRange.all { it.attrs.link == link } },
        )
    }

    fun toggle(mark: Mark) {
        val sel = selection
        if (sel.collapsed) {
            val current = typing ?: attrsAt(sel.start).plain()
            typing = current.copy(annotations = current.annotations.with(mark, !current.annotations.has(mark)))
            return
        }
        val inRange = runsIn(sel.min, sel.max)
        val allOn = inRange.isNotEmpty() && inRange.all { it.attrs.annotations.has(mark) }
        mapRange(sel.min, sel.max) { it.copy(annotations = it.annotations.with(mark, !allOn)) }
    }

    fun setColor(color: NColor) {
        val sel = selection
        if (sel.collapsed) {
            val current = typing ?: attrsAt(sel.start).plain()
            typing = current.copy(annotations = current.annotations.copy(color = color))
            return
        }
        mapRange(sel.min, sel.max) { it.copy(annotations = it.annotations.copy(color = color)) }
    }

    fun setLink(url: String?) {
        val clean = url?.trim()?.ifBlank { null }
        val sel = selection
        if (sel.collapsed) {
            if (clean != null) insertAtomic(sel.start, sel.start, clean, Attrs(link = clean))
            return
        }
        mapRange(sel.min, sel.max) { it.copy(link = clean) }
    }

    fun clearFormatting() {
        val sel = selection
        if (sel.collapsed) {
            typing = Attrs()
            return
        }
        mapRange(sel.min, sel.max) { Attrs(mention = it.mention, equation = it.equation) }
    }

    /** Replaces [start, end) of the content with an atomic mention or equation. */
    fun insertAtomic(start: Int, end: Int, display: String, attrs: Attrs) {
        val s = start.coerceIn(0, content.length)
        val e = end.coerceIn(s, content.length)
        lastContent = content
        textState.edit {
            replace(s + 1, e + 1, "$display ")
            selection = TextRange(s + 1 + display.length + 1)
        }
        val current = content
        val tail = applyEdit(runs, s, e, 0, Attrs())
        runs = merge(insertRun(tail, s, listOf(Run(display.length, attrs), Run(1, Attrs(annotations = attrs.annotations.copy(color = NColor.Default))))))
        if (runs.sumOf { it.length } != current.length) runs = listOf(Run(current.length, Attrs()))
        lastContent = current
        lastSelection = textState.selection
    }

    /** Replaces [start, end) of the content with plain text (used when completing menus). */
    fun deleteRange(start: Int, end: Int) {
        val s = start.coerceIn(0, content.length)
        val e = end.coerceIn(s, content.length)
        textState.edit {
            replace(s + 1, e + 1, "")
            selection = TextRange(s + 1)
        }
        sync()
    }

    private fun insertRun(source: List<Run>, at: Int, add: List<Run>): List<Run> {
        val out = mutableListOf<Run>()
        var pos = 0
        var done = false
        for (run in source) {
            val re = pos + run.length
            if (!done && at <= pos) {
                out += add; done = true
                out += run
            } else if (!done && at < re) {
                out += Run(at - pos, run.attrs)
                out += add; done = true
                out += Run(re - at, run.attrs)
            } else out += run
            pos = re
        }
        if (!done) out += add
        return out
    }

    fun toRichText(): List<RichText> {
        val text = content
        if (runs.sumOf { it.length } != text.length) return if (text.isEmpty()) emptyList() else listOf(RichText(text))
        var pos = 0
        return runs.mapNotNull { run ->
            val piece = text.substring(pos, pos + run.length)
            pos += run.length
            if (piece.isEmpty()) null
            else RichText(piece, run.attrs.annotations, run.attrs.link, run.attrs.mention, run.attrs.equation)
        }
    }

    /** Splits the content at [at]; keeps the left part and returns the right part. */
    fun splitAt(at: Int): List<RichText> {
        val sel = selection
        if (!sel.collapsed) deleteRange(sel.min, sel.max)
        val splitPos = if (!sel.collapsed) sel.min else at.coerceIn(0, content.length)
        val all = toRichText()
        val left = mutableListOf<RichText>()
        val right = mutableListOf<RichText>()
        var pos = 0
        for (rt in all) {
            val end = pos + rt.text.length
            when {
                end <= splitPos -> left += rt
                pos >= splitPos -> right += rt
                rt.isMention || rt.isEquation -> if (splitPos - pos > rt.text.length / 2) left += rt else right += rt
                else -> {
                    left += rt.copy(text = rt.text.substring(0, splitPos - pos))
                    right += rt.copy(text = rt.text.substring(splitPos - pos))
                }
            }
            pos = end
        }
        replaceAll(left, left.plainText().length)
        return right
    }

    fun replaceAll(rich: List<RichText>, cursor: Int = -1) {
        val text = rich.plainText()
        textState.edit {
            replace(0, length, SENTINEL + text)
            selection = TextRange(1 + (if (cursor < 0) text.length else cursor.coerceIn(0, text.length)))
        }
        runs = runsFrom(rich)
        lastContent = text
        lastSelection = textState.selection
        typing = null
    }

    fun placeCursor(position: Int) {
        textState.edit { selection = TextRange(1 + position.coerceIn(0, length - 1)) }
        lastSelection = textState.selection
    }

    /** Detects a typed markdown shortcut at the very start of the block. */
    fun pendingShortcut(): Pair<String, BlockType>? {
        val text = content
        val cursor = selection.start
        return MarkdownShortcuts.firstOrNull { (prefix, _) -> text.startsWith(prefix) && cursor == prefix.length }
    }
}
