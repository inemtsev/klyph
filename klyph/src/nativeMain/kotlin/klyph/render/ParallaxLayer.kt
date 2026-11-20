package klyph.render

import klyph.entity.ComponentType

/**
 * A component that makes its entity's [Sprite] follow the camera at a fraction of the world's
 * speed, and optionally repeat across the view: a range of hills far away, a fence close by, a sky
 * that never ends. Add it to an entity that has a `Transform` and a [Sprite] and no parent.
 *
 * The factor says how much of the camera's movement the sprite shows. 0 sticks to the screen, 1
 * moves with the world as a sprite without this component does, a fraction is far away and
 * scrolls slowly, and more than 1 is nearer than the world and scrolls faster. [factorX] and
 * [factorY] are the factors along each axis; [factorY] starts as [factorX], so
 * `ParallaxLayer(0.5f)` scrolls at half speed both ways.
 *
 * The sprite is drawn at `transform + (1 - factor) * (view - anchor)` on each axis, where `view`
 * is the point the camera looks at and [anchorX] and [anchorY] are the anchor. While the camera
 * looks at the anchor the sprite is where its transform says, whatever the factor, and as the
 * camera moves away it moves `1 - factor` of the way with it. So to have a layer start at a chosen
 * place in the world, give that place as the transform and the point the camera looks at when the
 * game starts as the anchor. The anchor is 0 unless given, which draws the sprite at its transform
 * plus `1 - factor` times the view; at a factor of 1 the sprite is where its transform says, and at
 * 0 the transform is an offset from the center of the screen plus the anchor.
 * The point the camera looks at is [Camera.x] and [Camera.y] after the clamp to [Camera.bounds],
 * plus [Camera.offsetX] and [Camera.offsetY], so a sprite with a factor of 0 does not shake with
 * the screen and one with a factor of 1 does. An `Interpolated` entity is blended between its last
 * two fixed steps first and shifted after. The sprite zooms with the camera like everything else
 * in the world; the factor never multiplies the zoom. It draws in its [Sprite.layer], sorted with
 * every other sprite; among the sprites of one [Sprite.layer] and texture, those with this
 * component are drawn first, whatever the storage order. The shift is the sprite's alone:
 * `drawnPositionOf` returns the transform's position without it, and a `Text` on the same
 * entity is drawn where the transform is.
 *
 * [repeatX] and [repeatY] are the distance in world units from one copy of the sprite to the next
 * along an axis, or 0, the default, for no repeat along it. Set a repeat to the sprite's drawn
 * size on that axis, [Sprite.width] or [Sprite.height] times the entity's scale, for a strip whose
 * copies meet edge to edge. The engine draws every copy that overlaps the area the camera sees,
 * whatever the zoom, so the strip never ends; for a rotated camera that area is the smallest box
 * along the world's axes that holds the view. An axis with no repeat is not cut to the view, so
 * a layer with no repeat at all is drawn once, wherever it is. At most [MAX_COPIES] copies of one
 * sprite are drawn, row by row from the top-left, so a period far smaller than the view leaves
 * the far copies out; give the sprite a [Sprite.tileWidth] to repeat a small texture across a
 * large area instead.
 *
 * Drawing the sprite throws [IllegalStateException], naming its entity, when the entity has a
 * parent, when [factorX] or [factorY] is negative, infinite or not a number, when [repeatX] or
 * [repeatY] is negative, infinite or not a number, or when [anchorX] or [anchorY] is infinite or
 * not a number. Every parameter has the type `Float`, so pass every argument after the first by
 * name.
 *
 * [toString] gives the three pairs, for example
 * `ParallaxLayer(factor=0.5x0.5, repeat=1024.0x0.0, anchor=480.0x0.0)`.
 *
 * @throws IllegalStateException when the sprite is drawn, if its entity has a parent, a factor or
 *   a repeat is negative, infinite or not a number, or an anchor is infinite or not a number.
 *
 * @sample samples.render.ParallaxLayers.parallaxLayer
 */
public class ParallaxLayer(
    /**
     * How much of the camera's horizontal movement the sprite shows: 0 sticks to the screen, 1
     * moves with the world, a fraction is far away and more than 1 is nearer. Never negative.
     */
    public var factorX: Float,
    /** The factor along y, as [factorX] is along x; the same as [factorX] unless given. */
    public var factorY: Float = factorX,
    /**
     * Distance in world units between copies along x, or 0 for no repeat. For copies that meet edge
     * to edge, the sprite's [Sprite.width] times the entity's scale; nothing keeps the two in step.
     * Never negative.
     */
    public var repeatX: Float = 0f,
    /**
     * Distance in world units between copies along y, or 0 for no repeat. For copies that meet edge
     * to edge, the sprite's [Sprite.height] times the entity's scale; nothing keeps the two in step.
     * Never negative.
     */
    public var repeatY: Float = 0f,
    /**
     * The x in world units of the point the camera looks at when the sprite is drawn at its
     * transform's x, or 0, the default. Usually where the camera looks when the game starts. Any
     * finite value.
     */
    public var anchorX: Float = 0f,
    /** The anchor along y, as [anchorX] is along x; 0 unless given. */
    public var anchorY: Float = 0f,
) {
    /** Returns `ParallaxLayer(factor=0.5x0.5, repeat=1024.0x0.0, anchor=480.0x0.0)`. The format may change. */
    override fun toString(): String =
        "ParallaxLayer(factor=${factorX}x$factorY, repeat=${repeatX}x$repeatY, anchor=${anchorX}x$anchorY)"

    /** Component key for [ParallaxLayer], and its limit on copies. */
    public companion object : ComponentType<ParallaxLayer>("ParallaxLayer") {
        /**
         * The most copies of one sprite a [ParallaxLayer] draws in a frame, 256. Along one axis they
         * are the ones from the view's left or top edge; with a repeat on both axes they fill rows
         * from the top-left until the limit, so the last row may be partial. It stops a period far
         * smaller than the view, or a view zoomed far out, from drawing thousands of quads.
         */
        public const val MAX_COPIES: Int = 256
    }
}
