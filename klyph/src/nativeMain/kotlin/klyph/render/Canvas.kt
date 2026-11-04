package klyph.render

import klyph.platform.Color
import klyph.text.Font
import klyph.text.TextAlign
import klyph.text.TextShadow
import klyph.text.addText

/**
 * Queues quads, rectangles and text to draw this frame without creating entities, for a HUD,
 * menus, debug shapes and other things that change every frame. `App.canvas` is the one instance.
 *
 * Call it from [klyph.app.Phase.RENDER] systems only. The engine's sprite pass then draws the
 * queued items together with the world's sprites and `Text` components, sorted by [DrawSpace]
 * (every [DrawSpace.WORLD] item before every [DrawSpace.SCREEN] item), then by layer, lowest first,
 * then by texture, and empties the canvas, so each item is drawn in exactly one frame. Items of the
 * same space, layer and texture keep the order they were queued in; where items with different
 * textures overlap in one layer, which is on top is not specified, so give them different layers.
 *
 * Every draw call defaults to [DrawSpace.SCREEN], in points from the window's top-left corner;
 * pass `space = DrawSpace.WORLD` to draw in world units through `App.camera`. Rotations are in
 * radians, positive clockwise on screen. Several parameters share the type `Float`, so pass them
 * by name.
 *
 * The three calls anchor differently. [drawSprite] puts the quad's origin, its center by default,
 * on (x, y), as an entity's `Sprite`, a `Collider` and `Shape.Box` are centered on their
 * transform. [drawRect] puts its top-left corner on (x, y), as `Rect` does. [drawText] puts the
 * top of the first line at y and aligns each line on x, as a `Text` component does. So a debug
 * outline of a box collider drawn with [drawRect] starts half the box's size up and left of the
 * entity.
 *
 * The canvas grows as needed and allocates nothing once it has held a frame's items.
 *
 * Not thread-safe; call it from systems only. Samples live in the sandbox because drawing needs a
 * display.
 */
public class Canvas internal constructor(
    /** The region [drawRect] stretches: one white texel of the built-in font's atlas. */
    private val whitePixel: TextureRegion,
) {
    /** The queued quads, in the order they were added. The sprite pass reads them. */
    internal val quads: QuadBuffer = QuadBuffer()

    /** Whether calls are accepted now: from the start of RENDER until the sprite pass has read the canvas. */
    internal var isOpen: Boolean = false
        private set

    /** Number of quads queued for this frame: one per sprite or rectangle, one per visible glyph of text. */
    public val quadCount: Int get() = quads.count

    /**
     * Queues [region] as a quad of [width] by [height], tinted by [color], in [layer] of [space],
     * placed so that its origin lies on ([x], [y]) and turned by [rotation] radians about it.
     *
     * The origin is given as fractions of the quad's size from its top-left corner: ([originX],
     * [originY]) = (0.5, 0.5), the default, is the center, as for an entity's `Sprite`, and (0, 0)
     * the top-left corner. The size defaults to the region's size in texels, and the space to
     * [DrawSpace.SCREEN].
     *
     * @throws IllegalStateException if called outside [klyph.app.Phase.RENDER].
     */
    public fun drawSprite(
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float = region.width.toFloat(),
        height: Float = region.height.toFloat(),
        rotation: Float = 0f,
        color: Color = Color.WHITE,
        layer: Int = 0,
        space: DrawSpace = DrawSpace.SCREEN,
        originX: Float = 0.5f,
        originY: Float = 0.5f,
    ) {
        checkOpen()
        quads.addAt(region, x, y, width, height, rotation, originX, originY, color, layer, space)
    }

    /**
     * Queues a solid rectangle of [width] by [height] in [color] whose top-left corner is at
     * ([x], [y]), in [layer] of [space]. It is drawn from a white texel of the built-in font's
     * atlas, so rectangles and built-in text in one layer share a draw call. The default space is
     * [DrawSpace.SCREEN].
     *
     * @throws IllegalStateException if called outside [klyph.app.Phase.RENDER].
     */
    public fun drawRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
        layer: Int = 0,
        space: DrawSpace = DrawSpace.SCREEN,
    ) {
        checkOpen()
        quads.add(whitePixel, x + 0.5f * width, y + 0.5f * height, width, height, 0f, color, layer, space)
    }

    /**
     * Queues [text] drawn with [font] at [scale], tinted by [color], in [layer] of [space], one quad
     * per visible glyph.
     *
     * ([x], [y]) is the anchor: [y] is the top of the first line, and each line starts at [x],
     * is centered on it or ends at it as [align] says. Layout follows [Font]: `'\n'` starts a new
     * line [Font.lineHeight] times [scale] lower, kerning applies, and a character the font lacks
     * draws as its `'?'` glyph or not at all. The default space is [DrawSpace.SCREEN], as for the
     * other draw calls; `Font.measure` gives the size of the block.
     *
     * [text] may be a `StringBuilder` that the caller refills every frame: its characters are read
     * before this call returns, so text that changes every frame need not allocate a new `String`.
     *
     * With a [shadow], every glyph is first queued in the shadow's color at the shadow's offset, in
     * the same [layer], so the shadow draws under the text and the call queues twice the quads.
     *
     * @throws IllegalStateException if called outside [klyph.app.Phase.RENDER].
     */
    public fun drawText(
        font: Font,
        text: CharSequence,
        x: Float,
        y: Float,
        color: Color = Color.WHITE,
        scale: Float = 1f,
        align: TextAlign = TextAlign.LEFT,
        layer: Int = 0,
        space: DrawSpace = DrawSpace.SCREEN,
        shadow: TextShadow? = null,
    ) {
        checkOpen()
        quads.addText(font, text, x, y, rotation = 0f, scaleX = 1f, scaleY = 1f, scale, align, color, layer, space, shadow)
    }

    /** Starts accepting calls; the loop calls it as [klyph.app.Phase.RENDER] begins. */
    internal fun open() {
        isOpen = true
    }

    /** Empties the canvas and stops accepting calls; the sprite pass calls it once it has read it. */
    internal fun finish() {
        quads.clear()
        isOpen = false
    }

    private fun checkOpen() {
        check(isOpen) { "Draw calls are accepted only during Phase.RENDER, and this one came outside it." }
    }

    /** Returns `Canvas(quadCount=12)`. The format may change. */
    override fun toString(): String = "Canvas(quadCount=$quadCount)"
}
