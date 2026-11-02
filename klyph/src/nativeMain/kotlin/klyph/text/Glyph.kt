package klyph.text

import klyph.render.TextureRegion

/**
 * One character of a [Font]: the part of a font page that shows it and how it sits on the line.
 *
 * All distances are in the font's units, which are texels of its pages; text drawn at scale 1
 * covers one world unit or point per texel. [xOffset] and [yOffset] place the top-left corner of
 * [region] relative to the pen position and the top of the line, and [xAdvance] is how far the
 * pen moves right before the next character, before kerning.
 *
 * [region] is `null` for a character that has no pixels, such as a space: it moves the pen but
 * draws nothing.
 *
 * Has no sample because a [Font] needs textures, which need a GPU device that samples cannot open.
 */
public class Glyph internal constructor(
    /** The character this glyph draws. */
    public val char: Char,
    /** The part of a font page that shows the character, or `null` if it draws nothing. */
    public val region: TextureRegion?,
    /** Horizontal distance from the pen position to the left edge of [region]. */
    public val xOffset: Float,
    /** Vertical distance from the top of the line to the top edge of [region]. */
    public val yOffset: Float,
    /** Distance the pen moves right after this glyph, before kerning. */
    public val xAdvance: Float,
) {
    /**
     * Returns `Glyph(char='A', offset=(0.0, 0.0), xAdvance=8.0, region=TextureRegion(...))`. The
     * format may change.
     */
    override fun toString(): String = "Glyph(char='$char', offset=($xOffset, $yOffset), xAdvance=$xAdvance, region=$region)"
}
