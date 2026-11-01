package klyph.render

import klyph.entity.ComponentType
import klyph.platform.Color

/**
 * A quad of [width] by [height] world units centred on its entity's transform, showing [region]
 * tinted by [color].
 *
 * The size starts as the region's size in texels, so with a view measured in pixels and a scale
 * of 1 each texel covers one pixel. [isFlippedX] mirrors the region left to right and
 * [isFlippedY] mirrors it top to bottom; neither changes the quad's position. A sprite whose
 * [isVisible] is `false` is not drawn and costs no draw work.
 *
 * Sprites in a higher [layer] draw on top of sprites in a lower one. Within a layer, sprites are
 * grouped by texture, so where sprites with different textures overlap in one layer, which one
 * is on top is not specified; give them different layers. Every distinct pair of layer and
 * texture in a frame costs one draw call, so keep layers to a few draw-order groups such as
 * background, world and effects, not one per sprite.
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
) {
    /**
     * Returns `Sprite(size=16.0 x 32.0, color=Color(#ffffffff), layer=0, flippedX=false,
     * flippedY=false, visible=true, region=TextureRegion(...))`. The format may change.
     */
    override fun toString(): String =
        "Sprite(size=$width x $height, color=$color, layer=$layer, flippedX=$isFlippedX, flippedY=$isFlippedY, " +
            "visible=$isVisible, region=$region)"

    /** Component key for [Sprite]. */
    public companion object : ComponentType<Sprite>("Sprite")
}
