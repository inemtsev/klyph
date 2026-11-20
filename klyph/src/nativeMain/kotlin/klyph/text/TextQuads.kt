package klyph.text

import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.drawnTransform
import klyph.platform.Color
import klyph.platform.withAlpha
import klyph.render.DrawSpace
import klyph.render.QuadBuffer

/**
 * Appends one quad per visible glyph of [text] laid out with [font] at [scale] and [align], with
 * the anchor at ([x], [y]), the block scaled by [scaleX] and [scaleY] and turned by [rotation]
 * radians about the anchor. With a [shadow], first appends the same glyphs in the shadow's color,
 * its alpha times [color]'s, moved by the shadow's offset along the block's own axes, so the
 * shadow draws under the text in [layer]. Returns the number of quads added.
 *
 * Every glyph is written at the anchor with the anchor as its origin, so the vertex shader turns
 * it about the anchor as it turns a sprite about its origin, and nothing is rotated here.
 */
internal fun QuadBuffer.addText(
    font: Font,
    text: CharSequence,
    x: Float,
    y: Float,
    rotation: Float,
    scaleX: Float,
    scaleY: Float,
    scale: Float,
    align: TextAlign,
    color: Color,
    layer: Int,
    space: DrawSpace,
    shadow: TextShadow? = null,
): Int {
    val before = count
    if (shadow != null) {
        val shadowColor = shadow.color.withAlpha(shadow.color.alpha * color.alpha)
        val offsetX = shadow.offsetX * scaleX
        val offsetY = shadow.offsetY * scaleY
        addGlyphs(font, text, x, y, offsetX, offsetY, rotation, scaleX, scaleY, scale, align, shadowColor, layer, space)
    }
    addGlyphs(font, text, x, y, 0f, 0f, rotation, scaleX, scaleY, scale, align, color, layer, space)
    return count - before
}

/**
 * Appends one quad per visible glyph of [text], as [addText] does without a shadow, each glyph
 * moved by ([offsetX], [offsetY]) along the block's own axes before the turn.
 */
private fun QuadBuffer.addGlyphs(
    font: Font,
    text: CharSequence,
    x: Float,
    y: Float,
    offsetX: Float,
    offsetY: Float,
    rotation: Float,
    scaleX: Float,
    scaleY: Float,
    scale: Float,
    align: TextAlign,
    color: Color,
    layer: Int,
    space: DrawSpace,
) {
    font.layout(text, scale, align) { region, left, top ->
        val width = region.width * scale * scaleX
        val height = region.height * scale * scaleY
        // The glyph's top-left corner lies this far from the anchor along the block's axes. The origin is
        // the anchor as a fraction of the glyph's size, outside 0..1 for every glyph that does not cover
        // the anchor; the shader only multiplies and subtracts with it. A glyph of no width or height
        // draws nothing, and its origin is 0 so the record holds no NaN.
        val cornerX = left * scaleX + offsetX
        val cornerY = top * scaleY + offsetY
        val originX = if (width == 0f) 0f else -cornerX / width
        val originY = if (height == 0f) 0f else -cornerY / height
        addAt(region, x, y, width, height, rotation, originX, originY, color, layer, space)
    }
}

/**
 * Appends the glyph quads of every [Text] component of an entity with a [Transform] to
 * [destination], in [DrawSpace.WORLD], and returns how many were added. Each entity is placed at
 * its [WorldTransform] if it has one, else [alpha] of the way from its [PreviousTransform] to its
 * [Transform] if it has a previous transform from before its last step, else at its [Transform],
 * as sprites are, both through `drawnTransform`.
 */
internal fun World.extractTexts(alpha: Float, destination: QuadBuffer): Int {
    if (count(Text) == 0) return 0
    val before = destination.count
    forEach(Text, Transform) { entity, text, local ->
        val hierarchy = getOrNull(entity, WorldTransform)
        val previous = if (hierarchy == null) getOrNull(entity, PreviousTransform) else null
        drawnTransform(local, hierarchy, previous, alpha) { x, y, rotation, scaleX, scaleY ->
            destination.addText(
                text.font, text.text, x, y, rotation, scaleX, scaleY, text.scale, text.align, text.color, text.layer, DrawSpace.WORLD, text.shadow,
            )
        }
    }
    return destination.count - before
}
