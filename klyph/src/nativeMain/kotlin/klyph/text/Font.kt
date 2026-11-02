package klyph.text

import klyph.render.Texture

/**
 * A bitmap font: one [Glyph] per character it covers, drawn from [pageCount] textures, with
 * kerning between pairs of characters.
 *
 * Load one from a BMFont file with `Assets.font`, or use the engine's built-in 8 by 8 font,
 * `Assets.defaultFont`. Measure text with [measure]; draw it with `App.canvas.drawText` or a [Text]
 * component.
 *
 * Text is laid out line by line: `'\n'` starts a new line [lineHeight] below the last, `'\r'` is
 * ignored, and the pen moves right by each glyph's [Glyph.xAdvance] plus the kerning between it
 * and the glyph before it on the line. A character the font has no glyph for is drawn as the
 * font's `'?'` glyph if it has one and skipped otherwise. Characters outside the Basic
 * Multilingual Plane, which a `String` holds as two surrogate chars, are not supported.
 *
 * Has no sample because a font needs textures, which need a GPU device that samples cannot open.
 */
public class Font internal constructor(
    glyphs: Collection<Glyph>,
    kernings: Map<Int, Float>,
    /** Distance from the top of one line to the top of the next, in font units. */
    public val lineHeight: Float,
    /** Distance from the top of a line to the baseline the characters stand on, in font units. */
    public val baseline: Float,
    pages: List<Texture>,
) {
    /** The textures glyph regions point into, by BMFont page id. */
    internal val pages: List<Texture> = pages.toList()

    /** Number of textures the glyphs are drawn from. */
    public val pageCount: Int get() = pages.size

    /** Glyphs of the characters below [ASCII_SIZE], indexed by code. */
    private val asciiGlyphs = arrayOfNulls<Glyph>(ASCII_SIZE)

    /** Characters from [ASCII_SIZE] up that have a glyph, sorted, and their glyphs in the same order. */
    private val otherChars: CharArray
    private val otherGlyphs: Array<Glyph?>

    /** Kerning pairs in an open-addressing table: key `first shl 16 or second`, 0 marks an empty slot. */
    private val kerningKeys: IntArray
    private val kerningAmounts: FloatArray

    /** Number of kerning pairs. */
    internal val kerningCount: Int

    /** Number of glyphs. */
    internal val glyphCount: Int

    /** The glyph drawn for a character the font lacks: `'?'`, or `null` if the font lacks that too. */
    internal val fallback: Glyph?

    init {
        val others = ArrayList<Glyph>()
        for (glyph in glyphs) {
            if (glyph.char.code < ASCII_SIZE) asciiGlyphs[glyph.char.code] = glyph else others.add(glyph)
        }
        others.sortBy { it.char }
        otherChars = CharArray(others.size) { others[it].char }
        otherGlyphs = Array(others.size) { others[it] }
        glyphCount = asciiGlyphs.count { it != null } + others.size

        var tableSize = 8
        while (tableSize < kernings.size * 2) tableSize *= 2
        kerningKeys = IntArray(tableSize)
        kerningAmounts = FloatArray(tableSize)
        var stored = 0
        for ((key, amount) in kernings) {
            if (key == 0 || amount == 0f) continue
            var i = mix(key) and (tableSize - 1)
            while (kerningKeys[i] != 0 && kerningKeys[i] != key) i = (i + 1) and (tableSize - 1)
            if (kerningKeys[i] == 0) stored++
            kerningKeys[i] = key
            kerningAmounts[i] = amount
        }
        kerningCount = stored
        fallback = glyphOrNull('?')
    }

    /**
     * Returns the glyph that draws [char], or `null` if this font has none. Unlike drawing and
     * [measure], this does not fall back to `'?'`.
     */
    public fun glyphOrNull(char: Char): Glyph? {
        val code = char.code
        if (code < ASCII_SIZE) return asciiGlyphs[code]
        val chars = otherChars
        var low = 0
        var high = chars.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val found = chars[middle]
            when {
                found < char -> low = middle + 1
                found > char -> high = middle - 1
                else -> return otherGlyphs[middle]
            }
        }
        return null
    }

    /**
     * Returns the kerning between [first] and [second] when [second] directly follows [first]: the
     * distance, in font units, added to the pen's advance between them. It is negative for pairs
     * drawn closer together, such as "AV", and 0 for a pair the font does not list.
     */
    public fun kerningOf(first: Char, second: Char): Float {
        if (kerningCount == 0) return 0f
        val key = (first.code shl 16) or second.code
        if (key == 0) return 0f
        val mask = kerningKeys.size - 1
        var i = mix(key) and mask
        while (true) {
            val stored = kerningKeys[i]
            if (stored == key) return kerningAmounts[i]
            if (stored == 0) return 0f
            i = (i + 1) and mask
        }
    }

    /** Returns the glyph drawn for [char]: its own, the `'?'` fallback, or `null` to skip it. */
    internal fun glyphOrFallback(char: Char): Glyph? = glyphOrNull(char) ?: fallback

    /** Returns `Font(glyphs=95, kernings=0, lineHeight=8.0, baseline=7.0, pages=1)`. The format may change. */
    override fun toString(): String =
        "Font(glyphs=$glyphCount, kernings=$kerningCount, lineHeight=$lineHeight, baseline=$baseline, pages=$pageCount)"

    internal companion object {
        /** Characters below this code are found by direct index. */
        const val ASCII_SIZE: Int = 128

        /** Packs a kerning pair into the key [kerningOf] looks up. */
        fun kerningKey(first: Char, second: Char): Int = (first.code shl 16) or second.code

        private fun mix(key: Int): Int {
            val mixed = key * -1640531527
            return mixed xor (mixed ushr 15)
        }
    }
}
