package klyph.text

import klyph.render.Texture
import klyph.render.TextureRegion

/**
 * Parses [text], the contents of the BMFont text file at [path], into a [Font] whose pages come
 * from [loadPage], called once per page in id order with the `file` of its `page` line.
 *
 * Reads the `common` line (`lineHeight`, `base`), the `page` lines (`id`, `file`), the `char`
 * lines (`id`, `x`, `y`, `width`, `height`, `xoffset`, `yoffset`, `xadvance`, `page`) and the
 * `kerning` lines (`first`, `second`, `amount`). Other lines and keys, such as `info`, `chars`,
 * `kernings` and `chnl`, are ignored. Characters and kerning pairs above U+FFFF are skipped,
 * because a [Char] cannot hold them. A glyph with zero width or height gets no region. The whole
 * file is checked before any page is loaded.
 *
 * @throws FontException if [text] is a binary BMFont (it starts with `BMF`), a value is not a
 *   number, a required line or key is missing, the pages are not numbered from 0 without gaps, a
 *   glyph names an undeclared page, or a glyph lies outside its page.
 */
internal fun parseBmFont(text: String, path: String, loadPage: (fileName: String) -> Texture): Font {
    if (text.startsWith("BMF")) {
        throw FontException("Font file '$path' is a binary BMFont; only the BMFont text format is supported.")
    }
    var lineHeight = Float.NaN
    var baseline = 0f
    val pageFiles = HashMap<Int, String>()
    val chars = ArrayList<RawGlyph>()
    val kernings = HashMap<Int, Float>()
    var lineNumber = 0
    for (rawLine in text.lineSequence()) {
        lineNumber++
        val line = BmFontLine.parseOrNull(rawLine, path, lineNumber) ?: continue
        when (line.tag) {
            "common" -> {
                lineHeight = line.float("lineHeight")
                baseline = line.float("base")
            }
            "page" -> pageFiles[line.int("id")] = line.string("file")
            "char" -> {
                val id = line.int("id")
                if (id in 0..0xFFFF) {
                    chars += RawGlyph(
                        id = id,
                        x = line.int("x"),
                        y = line.int("y"),
                        width = line.int("width"),
                        height = line.int("height"),
                        xOffset = line.float("xoffset"),
                        yOffset = line.float("yoffset"),
                        xAdvance = line.float("xadvance"),
                        page = line.intOrDefault("page", 0),
                        lineNumber = lineNumber,
                    )
                }
            }
            "kerning" -> {
                val first = line.int("first")
                val second = line.int("second")
                val amount = line.float("amount")
                if (first in 0..0xFFFF && second in 0..0xFFFF) kernings[Font.kerningKey(first.toChar(), second.toChar())] = amount
            }
        }
    }
    if (lineHeight.isNaN()) throw FontException("Font file '$path' has no 'common' line.")
    for (id in 0 until pageFiles.size) {
        if (id !in pageFiles) throw FontException("Font file '$path' has page ids ${pageFiles.keys.sorted()}, which are not numbered from 0 without gaps.")
    }
    for (glyph in chars) {
        if (glyph.page !in pageFiles) {
            throw FontException("Font file '$path' line ${glyph.lineNumber} uses page ${glyph.page}, which no 'page' line declares.")
        }
    }
    val pages = List(pageFiles.size) { loadPage(pageFiles.getValue(it)) }
    val glyphs = chars.map { raw ->
        val region = if (raw.width > 0 && raw.height > 0) {
            try {
                TextureRegion(pages[raw.page], raw.x, raw.y, raw.width, raw.height)
            } catch (e: IllegalArgumentException) {
                throw FontException("Font file '$path' line ${raw.lineNumber} places a glyph outside page ${raw.page}.", e)
            }
        } else {
            null
        }
        Glyph(raw.id.toChar(), region, raw.xOffset, raw.yOffset, raw.xAdvance)
    }
    return Font(glyphs, kernings, lineHeight, baseline, pages)
}

/** One `char` line as read, before its page is loaded. */
private class RawGlyph(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val xOffset: Float,
    val yOffset: Float,
    val xAdvance: Float,
    val page: Int,
    val lineNumber: Int,
) {
    override fun toString(): String = "RawGlyph(id=$id, line=$lineNumber)"
}

/**
 * One line of a BMFont text file: a tag such as `char`, then `key=value` pairs. A value may be
 * quoted to hold spaces; the quotes are removed.
 */
private class BmFontLine(val tag: String, private val values: Map<String, String>, private val path: String, private val lineNumber: Int) {
    fun string(key: String): String =
        values[key] ?: throw FontException("Font file '$path' line $lineNumber has no '$key' value.")

    fun int(key: String): Int {
        val value = string(key)
        return value.toIntOrNull() ?: throw notANumber(key, value)
    }

    fun intOrDefault(key: String, default: Int): Int {
        val value = values[key] ?: return default
        return value.toIntOrNull() ?: throw notANumber(key, value)
    }

    fun float(key: String): Float {
        val value = string(key)
        return value.toFloatOrNull()?.takeIf { it.isFinite() } ?: throw notANumber(key, value)
    }

    private fun notANumber(key: String, value: String): FontException =
        FontException("Font file '$path' line $lineNumber has '$value' for '$key', which is not a number.")

    override fun toString(): String = "BmFontLine(tag=$tag, values=$values)"

    companion object {
        /** Splits [line] into its tag and values, or returns `null` for a blank line. */
        fun parseOrNull(line: String, path: String, lineNumber: Int): BmFontLine? {
            var i = 0
            val length = line.length
            while (i < length && line[i].isWhitespace()) i++
            if (i == length) return null
            val tagStart = i
            while (i < length && !line[i].isWhitespace()) i++
            val tag = line.substring(tagStart, i)
            val values = HashMap<String, String>()
            while (i < length) {
                while (i < length && line[i].isWhitespace()) i++
                if (i == length) break
                val keyStart = i
                while (i < length && line[i] != '=' && !line[i].isWhitespace()) i++
                val key = line.substring(keyStart, i)
                if (i == length || line[i] != '=') continue
                i++
                val value: String
                if (i < length && line[i] == '"') {
                    val close = line.indexOf('"', i + 1)
                    val end = if (close < 0) length else close
                    value = line.substring(i + 1, end)
                    i = if (close < 0) length else close + 1
                } else {
                    val valueStart = i
                    while (i < length && !line[i].isWhitespace()) i++
                    value = line.substring(valueStart, i)
                }
                values[key] = value
            }
            return BmFontLine(tag, values, path, lineNumber)
        }
    }
}
