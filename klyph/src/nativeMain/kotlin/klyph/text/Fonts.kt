package klyph.text

import klyph.math.Vec2
import klyph.render.TextureRegion

/**
 * Returns the size of [text] drawn with this font at [scale]: the width of its widest line and
 * the height of all its lines, each [Font.lineHeight] tall.
 *
 * Lines are split at `'\n'`, so an empty string is one empty line, zero wide and one line high,
 * and a trailing `'\n'` adds an empty line. A line's width is the sum of its glyphs'
 * [Glyph.xAdvance] and the kerning between them; characters without a glyph count as the `'?'`
 * glyph or, if the font lacks it, as nothing. [scale] multiplies both dimensions.
 *
 * Has no sample because a [Font] needs textures, which need a GPU device that samples cannot open.
 */
public fun Font.measure(text: CharSequence, scale: Float = 1f): Vec2 {
    var widest = 0f
    var lines = 0
    forEachLine(text) { start, end ->
        val width = lineWidth(text, start, end)
        if (width > widest) widest = width
        lines++
    }
    return Vec2(widest * scale, lines * lineHeight * scale)
}

/** Calls [action] with the start and end index of every line of [text], split at `'\n'`. */
internal inline fun forEachLine(text: CharSequence, action: (start: Int, end: Int) -> Unit) {
    var start = 0
    while (true) {
        val newline = text.indexOf('\n', start)
        val end = if (newline < 0) text.length else newline
        action(start, end)
        if (newline < 0) return
        start = newline + 1
    }
}

/**
 * Returns the width in font units of the characters of [text] from [start] until [end], one line
 * with no `'\n'`: the advances of their glyphs plus the kerning between neighbors.
 */
internal fun Font.lineWidth(text: CharSequence, start: Int, end: Int): Float {
    var width = 0f
    var previous = NO_CHAR
    for (i in start until end) {
        val char = text[i]
        if (char == '\r') continue
        val glyph = glyphOrFallback(char) ?: continue
        if (previous != NO_CHAR) width += kerningOf(previous.toChar(), glyph.char)
        width += glyph.xAdvance
        previous = glyph.char.code
    }
    return width
}

/**
 * Lays out [text] at [scale] with [align] and calls [emit] for every glyph that draws something,
 * with its region and the position of the region's top-left corner relative to the anchor: x
 * from the anchor as [align] places each line, y down from the top of the first line. Both are
 * already multiplied by [scale]. Allocates nothing.
 */
internal inline fun Font.layout(text: CharSequence, scale: Float, align: TextAlign, emit: (region: TextureRegion, left: Float, top: Float) -> Unit) {
    var top = 0f
    val lineHeight = lineHeight
    forEachLine(text) { start, end ->
        var pen = when (align) {
            TextAlign.LEFT -> 0f
            TextAlign.CENTER -> -0.5f * lineWidth(text, start, end)
            TextAlign.RIGHT -> -lineWidth(text, start, end)
        }
        var previous = NO_CHAR
        for (i in start until end) {
            val char = text[i]
            if (char == '\r') continue
            val glyph = glyphOrFallback(char) ?: continue
            if (previous != NO_CHAR) pen += kerningOf(previous.toChar(), glyph.char)
            val region = glyph.region
            if (region != null) emit(region, (pen + glyph.xOffset) * scale, (top + glyph.yOffset) * scale)
            pen += glyph.xAdvance
            previous = glyph.char.code
        }
        top += lineHeight
    }
}

/** Marks "no previous character" in the layout loops, outside the range of `Char.code`. */
internal const val NO_CHAR: Int = -1
