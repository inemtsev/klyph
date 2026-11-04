package klyph.text

import klyph.platform.Color

/**
 * A drop shadow for text: a copy of every glyph tinted by [color] and moved by [offsetX] and
 * [offsetY], drawn under the text in the same layer so the text reads on a busy background.
 *
 * The offset is in the same units as the text's position, points for `Canvas.drawText` in screen
 * space and world units otherwise, along the text's own axes: it turns with a rotated [Text] and
 * grows with its entity's scale, but not with the text's own scale. The shadow's alpha is [color]'s
 * alpha times the alpha of the text's color, so a fading text takes its shadow with it. Every
 * shadowed glyph costs a second quad.
 *
 * Two shadows are equal when their offsets and colors are. [toString] prints
 * `TextShadow(offsetX=2.0, offsetY=2.0, color=Color(#000000b4))`.
 *
 * @throws IllegalArgumentException if [offsetX] or [offsetY] is not finite.
 *
 * @sample samples.text.TextShadows.textShadow
 */
public class TextShadow(
    /** How far right of the text the shadow lies; negative moves it left. */
    public val offsetX: Float = 2f,
    /** How far below the text the shadow lies; negative moves it up. */
    public val offsetY: Float = 2f,
    /** The tint of the shadow glyphs. The default is black at 70% alpha. */
    public val color: Color = Color(0x000000B4u),
) {
    init {
        require(offsetX.isFinite() && offsetY.isFinite()) { "Shadow offset ($offsetX, $offsetY) is not finite." }
    }

    override fun equals(other: Any?): Boolean =
        other is TextShadow && offsetX.equals(other.offsetX) && offsetY.equals(other.offsetY) && color == other.color

    override fun hashCode(): Int {
        var result = offsetX.hashCode()
        result = 31 * result + offsetY.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }

    override fun toString(): String = "TextShadow(offsetX=$offsetX, offsetY=$offsetY, color=$color)"
}
