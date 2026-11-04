package klyph.text

import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.blendRotation
import klyph.platform.Color
import klyph.platform.withAlpha
import klyph.render.DrawSpace
import klyph.render.QuadBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Appends one quad per visible glyph of [text] laid out with [font] at [scale] and [align], with
 * the anchor at ([x], [y]), the block scaled by [scaleX] and [scaleY] and turned by [rotation]
 * radians about the anchor. With a [shadow], first appends the same glyphs in the shadow's color,
 * its alpha times [color]'s, with the anchor moved by the shadow's offset along the block's own
 * axes, so the shadow draws under the text in [layer]. Returns the number of quads added.
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
        val dx = shadow.offsetX * scaleX
        val dy = shadow.offsetY * scaleY
        if (rotation == 0f) {
            addGlyphs(font, text, x + dx, y + dy, rotation, scaleX, scaleY, scale, align, shadowColor, layer, space)
        } else {
            val c = cos(rotation)
            val s = sin(rotation)
            addGlyphs(font, text, x + dx * c - dy * s, y + dx * s + dy * c, rotation, scaleX, scaleY, scale, align, shadowColor, layer, space)
        }
    }
    addGlyphs(font, text, x, y, rotation, scaleX, scaleY, scale, align, color, layer, space)
    return count - before
}

/** Appends one quad per visible glyph of [text], as [addText] does without a shadow. */
private fun QuadBuffer.addGlyphs(
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
) {
    if (rotation == 0f) {
        font.layout(text, scale, align) { region, left, top ->
            val width = region.width * scale
            val height = region.height * scale
            add(
                region,
                x + (left + 0.5f * width) * scaleX,
                y + (top + 0.5f * height) * scaleY,
                width * scaleX,
                height * scaleY,
                0f,
                color,
                layer,
                space,
            )
        }
    } else {
        val c = cos(rotation)
        val s = sin(rotation)
        font.layout(text, scale, align) { region, left, top ->
            val width = region.width * scale
            val height = region.height * scale
            val cx = (left + 0.5f * width) * scaleX
            val cy = (top + 0.5f * height) * scaleY
            add(region, x + cx * c - cy * s, y + cx * s + cy * c, width * scaleX, height * scaleY, rotation, color, layer, space)
        }
    }
}

/**
 * Appends the glyph quads of every [Text] component of an entity with a [Transform] to
 * [destination], in [DrawSpace.WORLD], and returns how many were added. Each entity is placed at
 * its [WorldTransform] if it has one, else [alpha] of the way from its [PreviousTransform] to its
 * [Transform] if it has a previous transform from before its last step, else at its [Transform],
 * as sprites are.
 */
internal fun World.extractTexts(alpha: Float, destination: QuadBuffer): Int {
    if (count(Text) == 0) return 0
    val before = destination.count
    forEach(Text, Transform) { entity, text, local ->
        val hierarchy = getOrNull(entity, WorldTransform)
        val previous = if (hierarchy == null) getOrNull(entity, PreviousTransform) else null
        val x: Float
        val y: Float
        val rotation: Float
        val scaleX: Float
        val scaleY: Float
        if (hierarchy != null) {
            x = hierarchy.x
            y = hierarchy.y
            rotation = hierarchy.rotation
            scaleX = hierarchy.scaleX
            scaleY = hierarchy.scaleY
        } else if (previous != null && !previous.isFirstStep) {
            x = previous.x + alpha * (local.x - previous.x)
            y = previous.y + alpha * (local.y - previous.y)
            rotation = blendRotation(previous.rotation, local.rotation, alpha)
            scaleX = previous.scaleX + alpha * (local.scaleX - previous.scaleX)
            scaleY = previous.scaleY + alpha * (local.scaleY - previous.scaleY)
        } else {
            x = local.x
            y = local.y
            rotation = local.rotation
            scaleX = local.scaleX
            scaleY = local.scaleY
        }
        destination.addText(
            text.font, text.text, x, y, rotation, scaleX, scaleY, text.scale, text.align, text.color, text.layer, DrawSpace.WORLD, text.shadow,
        )
    }
    return destination.count - before
}
