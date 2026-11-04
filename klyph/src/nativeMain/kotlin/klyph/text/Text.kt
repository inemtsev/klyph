package klyph.text

import klyph.entity.ComponentType
import klyph.platform.Color

/**
 * A component that draws [text] with [font] in world space at its entity's transform.
 *
 * The entity's position is the text's anchor: each line starts at it, is centered on it or ends at
 * it as [align] says, and the first line's top is at it, so with [TextAlign.LEFT] the anchor is
 * the block's top-left corner. The entity's rotation turns the whole block about the anchor and
 * its scale multiplies [scale]. Like a sprite, an entity in a hierarchy is drawn at its
 * `WorldTransform`, an `Interpolated` one blended between its last two fixed steps, and any other
 * at its `Transform`. Without a `Transform` nothing is drawn.
 *
 * Every visible glyph is one quad in [layer], sorted with the world's sprites; layout follows
 * [Font]: `'\n'` starts a new line, kerning applies, and a character the font lacks draws as its
 * `'?'` glyph or not at all. Text is laid out again every frame, so changing [text] costs nothing
 * extra, and [text] may be a `StringBuilder` the game refills in place, so a score that changes
 * every frame need not allocate a new `String`. Each glyph counts in `FrameStats.spriteCount`.
 * With a [shadow], every glyph is drawn a second time under the text, in the same layer.
 *
 * For text that stays on screen regardless of the camera, such as a score, use `App.canvas.drawText`
 * in [klyph.render.DrawSpace.SCREEN] instead.
 *
 * Has no sample because a [Font] needs textures, which need a GPU device that samples cannot open.
 */
public class Text(
    /** The font the glyphs come from. */
    public var font: Font,
    /** The characters drawn; may hold several lines. Read every frame, so changes to a `StringBuilder` show at once. */
    public var text: CharSequence,
    /** Tint multiplied with every glyph texel. [Color.WHITE] shows the font's own colors. */
    public var color: Color = Color.WHITE,
    /** Size multiplier in world units per font unit, before the entity's scale. */
    public var scale: Float = 1f,
    /** Where each line sits relative to the entity's position. */
    public var align: TextAlign = TextAlign.LEFT,
    /** Draw-order group shared with sprites; higher layers draw on top. */
    public var layer: Int = 0,
    /** A drop shadow drawn under every glyph, or `null`, the default, for none. */
    public var shadow: TextShadow? = null,
) {
    /**
     * Returns `Text(text="Score: 10", color=Color(#ffffffff), scale=1.0, align=LEFT, layer=0,
     * shadow=null, font=Font(...))`. The format may change.
     */
    override fun toString(): String =
        "Text(text=\"$text\", color=$color, scale=$scale, align=$align, layer=$layer, shadow=$shadow, font=$font)"

    /** Component key for [Text]. */
    public companion object : ComponentType<Text>("Text")
}
