package klyph.render

import klyph.entity.ComponentType
import klyph.platform.Color

/**
 * A quad of [width] by [height] world units centered on its entity's transform, showing [region]
 * tinted by [color].
 *
 * The size starts as the region's size in texels, so through `App.camera` at zoom 1 and a scale
 * of 1 each texel covers one point, which is two pixels on a display of pixel density 2.
 * [isFlippedX] mirrors the region left to right and
 * [isFlippedY] mirrors it top to bottom; neither changes the quad's position. A sprite whose
 * [isVisible] is `false` is not drawn and costs no draw work.
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
 * repeat short. A tile size of 0, the default, stretches the region across that dimension as
 * usual, so setting only [tileWidth] repeats the texture along the quad and stretches it down.
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
) {
    /**
     * Returns `Sprite(size=16.0x32.0, color=Color(#ffffffff), layer=0, flippedX=false,
     * flippedY=false, visible=true, region=TextureRegion(...))`, with `tile=8.0x8.0` after the
     * size when a tile size is set. The format may change.
     */
    override fun toString(): String {
        val tile = if (tileWidth != 0f || tileHeight != 0f) ", tile=${tileWidth}x$tileHeight" else ""
        return "Sprite(size=${width}x$height$tile, color=$color, layer=$layer, flippedX=$isFlippedX, " +
            "flippedY=$isFlippedY, visible=$isVisible, region=$region)"
    }

    /** Component key for [Sprite]. */
    public companion object : ComponentType<Sprite>("Sprite")
}
