package klyph.render

import klyph.entity.ComponentType
import klyph.platform.Color

/**
 * A quad of [width] by [height] world units showing [region] tinted by [color], placed so that its
 * origin lies on its entity's transform.
 *
 * The origin is given as fractions of the quad's size from its top-left corner: ([originX],
 * [originY]) = (0.5, 0.5), the default, is the center, and (0, 0) the top-left corner. Because y
 * points down, (1, 1) is the bottom-right corner and (0.5, 1) the middle of the bottom edge, where
 * the feet of a standing character go. The transform's rotation turns the quad about the origin
 * and its scale grows the quad away from it, so the point on the origin stays where the transform
 * puts it. [isFlippedX] mirrors the region left to right and [isFlippedY] mirrors it top to
 * bottom, both about the origin, so the point on the origin stays put and the rest of the picture
 * changes sides.
 *
 * Values outside 0 to 1 put the origin outside the quad and are fine. Drawing a sprite throws
 * [IllegalStateException], naming its entity, when an origin is infinite or not a number. A
 * `Collider` does not follow the origin: it stays centered on the transform plus its own offset,
 * so a character with its feet on the transform, origin (0.5, 1), lifts its collision box with a
 * negative `offsetY`, half the box's height for a box as tall as the sprite.
 *
 * The size starts as the region's size in texels, so through `App.camera` at zoom 1 and a scale
 * of 1 each texel covers one point, which is two pixels on a display of pixel density 2. A sprite
 * whose [isVisible] is `false` is not drawn and costs no draw work. Several parameters share the
 * type `Float` or `Boolean`, so pass those after [height] by name.
 *
 * Sprites in a higher [layer] draw on top of sprites in a lower one. Within a layer, sprites are
 * grouped by texture, so where sprites with different textures overlap in one layer, which one
 * is on top is not specified; give them different layers. Every distinct pair of layer and
 * texture in a frame costs one draw call, so keep layers to a few draw-order groups such as
 * background, world and effects, not one per sprite.
 *
 * Set [tileWidth] and [tileHeight] to repeat the texture across the quad instead of stretching
 * it, for a floor, a wall or a backdrop that is larger than its art. Each is the size of one
 * repeat in world units before the entity's scale, as [width] and [height] are, so the quad shows
 * `width / tileWidth` repeats across and `height / tileHeight` down, and the entity's scale
 * enlarges the repeats along with the rest of the quad. A count that is not whole cuts the last
 * repeat short, and the repeats start at the quad's top-left corner wherever the origin puts it.
 * A tile size of 0, the default, stretches the region across that dimension as usual, so setting
 * only [tileWidth] repeats the texture along the quad and stretches it down.
 *
 * Repeating needs a texture created with [klyph.platform.SamplerAddress.REPEAT], such as one
 * loaded with `App.assets.texture(path, address = SamplerAddress.REPEAT)`, and a [region] that
 * covers the whole of it, as `Texture.asRegion` makes it. Drawing a sprite throws
 * [IllegalStateException], naming its entity, when it has a tile size and its texture or region
 * is not like that, or when a tile size is negative.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 */
public class Sprite(
    /** The part of a texture the quad shows. */
    public var region: TextureRegion,
    /** Width in world units before the entity's scale. */
    public var width: Float = region.width.toFloat(),
    /** Height in world units before the entity's scale. */
    public var height: Float = region.height.toFloat(),
    /** Tint multiplied with every texel. [Color.WHITE] shows the texture unchanged. */
    public var color: Color = Color.WHITE,
    /** Draw-order group; higher layers draw on top. Any `Int`, negative included. */
    public var layer: Int = 0,
    /** Whether the region is mirrored left to right. */
    public var isFlippedX: Boolean = false,
    /** Whether the region is mirrored top to bottom. */
    public var isFlippedY: Boolean = false,
    /** Whether the sprite is drawn. */
    public var isVisible: Boolean = true,
    /**
     * Width of one repeat of the texture in world units before the entity's scale, or 0 to stretch
     * the region across [width]. Never negative.
     */
    public var tileWidth: Float = 0f,
    /**
     * Height of one repeat of the texture in world units before the entity's scale, or 0 to
     * stretch the region across [height]. Never negative.
     */
    public var tileHeight: Float = 0f,
    /**
     * Fraction of [width], from the left edge, where the entity's `Transform` lies: 0 is the left
     * edge, 0.5, the default, the center and 1 the right edge. May lie outside 0 to 1, but is never
     * infinite or not a number.
     */
    public var originX: Float = 0.5f,
    /**
     * Fraction of [height], from the top edge, where the entity's `Transform` lies: 0 is the top
     * edge, 0.5, the default, the center and 1 the bottom edge, because y points down. May lie
     * outside 0 to 1, but is never infinite or not a number.
     */
    public var originY: Float = 0.5f,
) {
    /**
     * Returns `Sprite(size=16.0x32.0, color=Color(#ffffffff), layer=0, flippedX=false,
     * flippedY=false, visible=true, region=TextureRegion(...))`, with `origin=0.5x1.0` and
     * `tile=8.0x8.0` after the size when the origin is not the center or a tile size is set. The
     * format may change.
     */
    override fun toString(): String {
        val origin = if (originX != 0.5f || originY != 0.5f) ", origin=${originX}x$originY" else ""
        val tile = if (tileWidth != 0f || tileHeight != 0f) ", tile=${tileWidth}x$tileHeight" else ""
        return "Sprite(size=${width}x$height$origin$tile, color=$color, layer=$layer, flippedX=$isFlippedX, " +
            "flippedY=$isFlippedY, visible=$isVisible, region=$region)"
    }

    /** Component key for [Sprite]. */
    public companion object : ComponentType<Sprite>("Sprite")
}
